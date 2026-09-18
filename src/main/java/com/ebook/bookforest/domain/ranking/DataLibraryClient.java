package com.ebook.bookforest.domain.ranking;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DataLibraryClient {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String apiKey;

    public DataLibraryClient(
        RestClient.Builder builder,
        ObjectMapper mapper,
        @Value("${app.ranking.api-key:}") String apiKey,
        @Value("${app.ranking.base-url:https://data4library.kr/api}") String baseUrl,
        @Value("${app.ranking.read-timeout:30s}") Duration readTimeout
    ) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(readTimeout);
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
        this.mapper = mapper;
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Result fetch(RankingType type, LocalDate today) {
        String json = client.get().uri(uri -> {
            uri.path(type == RankingType.POPULAR ? "/loanItemSrch" : "/hotTrend")
                .queryParam("authKey", apiKey).queryParam("format", "json");
            if (type == RankingType.POPULAR) {
                uri.queryParam("startDt", today.minusDays(30)).queryParam("endDt", today)
                    .queryParam("pageNo", 1).queryParam("pageSize", 50);
            } else {
                uri.queryParam("searchDt", today);
            }
            return uri.build();
        }).retrieve().body(String.class);
        return parse(mapper.readTree(json), type, today);
    }

    static Result parse(JsonNode root, RankingType type, LocalDate today) {
        JsonNode response = root == null ? null : root.get("response");
        if (response == null || !response.isObject() || response.has("error")) {
            throw new IllegalArgumentException("Invalid library ranking response");
        }
        LocalDate sourceDate = today;
        JsonNode docs = response.path("docs");
        if (type == RankingType.TRENDING) {
            JsonNode results = response.path("results");
            if (!results.isArray()) throw new IllegalArgumentException("Missing trend dates");
            JsonNode latest = null;
            LocalDate latestDate = null;
            for (JsonNode entry : results) {
                JsonNode result = entry.path("result");
                LocalDate date = LocalDate.parse(text(result, "date"));
                JsonNode dayDocs = result.path("docs");
                if (!dayDocs.isArray()) throw new IllegalArgumentException("Missing ranking books");
                if (dayDocs.isEmpty()) continue;
                if (!date.isAfter(today) && (latestDate == null || date.isAfter(latestDate))) {
                    latest = result;
                    latestDate = date;
                }
            }
            if (latest == null) throw new IllegalArgumentException("No populated trend date");
            sourceDate = latestDate;
            docs = latest.path("docs");
        }
        if (!docs.isArray()) throw new IllegalArgumentException("Missing ranking books");
        var books = new ArrayList<Item>();
        for (JsonNode entry : docs) {
            JsonNode doc = entry.path("doc");
            if (!doc.isObject()) throw new IllegalArgumentException("Invalid ranking book");
            int rank = Integer.parseInt(text(doc, type == RankingType.POPULAR ? "ranking" : "no"));
            if (rank < 1) throw new IllegalArgumentException("Invalid ranking position");
            String isbn = text(doc, "isbn13");
            if (isbn != null) {
                isbn = isbn.replaceAll("[\\s-]", "");
                if (!isbn.matches("\\d{13}")) isbn = null;
            }
            String title = text(doc, "bookname");
            String author = text(doc, "authors");
            if (title == null) continue;
            Long loans = type == RankingType.POPULAR ? Long.valueOf(text(doc, "loan_count")) : null;
            if (loans != null && loans < 0) throw new IllegalArgumentException("Invalid loan count");
            books.add(new Item(rank, isbn, title, author, text(doc, "publisher"),
                text(doc, "bookImageURL"), loans));
        }
        books.sort(Comparator.comparingInt(Item::rank));
        return new Result(sourceDate, List.copyOf(books));
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asString(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record Result(LocalDate sourceDate, List<Item> books) {
    }

    public record Item(int rank, String isbn13, String title, String author, String publisher,
                       String thumbnail, Long loanCount) {
    }
}
