package com.ebook.bookforest.domain.ranking.client;

import com.ebook.bookforest.domain.ranking.enums.RankingType;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 도서관 정보나루 순위 API 클라이언트
 *
 * <p>순위 종류별 API
 * <ul>
 *   <li>POPULAR: 대출 순위(loanItemSrch), 최근 30일, 50건</li>
 *   <li>TRENDING: 대출 급상승(hotTrend), 오늘 이전 날짜 중 순위가 있는 가장 최근 날짜</li>
 * </ul>
 */
@Component
public class DataLibraryClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final int POPULAR_PERIOD_DAYS = 30;
    private static final int POPULAR_PAGE_SIZE = 50;
    private static final String ISBN13_PATTERN = "\\d{13}";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public DataLibraryClient(
        RestClient.Builder builder,
        ObjectMapper objectMapper,
        @Value("${app.ranking.api-key:}") String apiKey,
        @Value("${app.ranking.base-url:https://data4library.kr/api}") String baseUrl,
        @Value("${app.ranking.read-timeout:30s}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    /** API 키 설정 여부, 없으면 순위 갱신을 하지 않음 */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 순위 조회
     *
     * @throws IllegalArgumentException 응답이 오류이거나 형식이 올바르지 않은 경우
     */
    public Result fetch(RankingType type, LocalDate today) {
        String json = restClient.get()
            .uri(uri -> buildUri(uri, type, today))
            .retrieve()
            .body(String.class);
        return parse(objectMapper.readTree(json), type, today);
    }

    private URI buildUri(UriBuilder uri, RankingType type, LocalDate today) {
        uri.path(type == RankingType.POPULAR ? "/loanItemSrch" : "/hotTrend")
            .queryParam("authKey", apiKey)
            .queryParam("format", "json");

        if (type == RankingType.POPULAR) {
            uri.queryParam("startDt", today.minusDays(POPULAR_PERIOD_DAYS))
                .queryParam("endDt", today)
                .queryParam("pageNo", 1)
                .queryParam("pageSize", POPULAR_PAGE_SIZE);
        } else {
            uri.queryParam("searchDt", today);
        }
        return uri.build();
    }

    /**
     * 순위 응답 해석
     * 제목이 없는 도서는 건너뛰고, 형식이 맞지 않는 ISBN은 null로 둔 뒤 순위순으로 정렬
     */
    static Result parse(JsonNode root, RankingType type, LocalDate today) {
        JsonNode response = root == null ? null : root.get("response");
        if (response == null || !response.isObject() || response.has("error")) {
            throw new IllegalArgumentException("도서관 정보나루 순위 응답이 올바르지 않습니다.");
        }

        LocalDate sourceDate = today;
        JsonNode docs = response.path("docs");
        if (type == RankingType.TRENDING) {
            JsonNode latest = findLatestTrend(response, today);
            sourceDate = LocalDate.parse(text(latest, "date"));
            docs = latest.path("docs");
        }
        if (!docs.isArray()) {
            throw new IllegalArgumentException("순위 도서 목록이 없습니다.");
        }

        List<Item> books = new ArrayList<>();
        for (JsonNode entry : docs) {
            Item item = parseItem(entry.path("doc"), type);
            if (item != null) {
                books.add(item);
            }
        }
        books.sort(Comparator.comparingInt(Item::rank));
        return new Result(sourceDate, List.copyOf(books));
    }

    /** 오늘 이전 날짜 중 순위 도서가 있는 가장 최근 날짜의 결과 */
    private static JsonNode findLatestTrend(JsonNode response, LocalDate today) {
        JsonNode results = response.path("results");
        if (!results.isArray()) {
            throw new IllegalArgumentException("급상승 순위 날짜 목록이 없습니다.");
        }

        JsonNode latest = null;
        LocalDate latestDate = null;
        for (JsonNode entry : results) {
            JsonNode result = entry.path("result");
            LocalDate date = LocalDate.parse(text(result, "date"));
            JsonNode dayDocs = result.path("docs");
            if (!dayDocs.isArray()) {
                throw new IllegalArgumentException("순위 도서 목록이 없습니다.");
            }
            if (dayDocs.isEmpty()) {
                continue;
            }
            if (!date.isAfter(today) && (latestDate == null || date.isAfter(latestDate))) {
                latest = result;
                latestDate = date;
            }
        }

        if (latest == null) {
            throw new IllegalArgumentException("순위 도서가 있는 급상승 날짜가 없습니다.");
        }
        return latest;
    }

    /** 순위 도서 한 건 해석, 제목이 없으면 null */
    private static Item parseItem(JsonNode doc, RankingType type) {
        if (!doc.isObject()) {
            throw new IllegalArgumentException("순위 도서 형식이 올바르지 않습니다.");
        }

        int rank = Integer.parseInt(text(doc, type == RankingType.POPULAR ? "ranking" : "no"));
        if (rank < 1) {
            throw new IllegalArgumentException("순위 값이 올바르지 않습니다.");
        }

        String title = text(doc, "bookname");
        if (title == null) {
            return null;
        }

        Long loanCount = type == RankingType.POPULAR ? Long.valueOf(text(doc, "loan_count")) : null;
        if (loanCount != null && loanCount < 0) {
            throw new IllegalArgumentException("대출 건수가 올바르지 않습니다.");
        }

        return new Item(
            rank,
            normalizeIsbn(text(doc, "isbn13")),
            title,
            text(doc, "authors"),
            text(doc, "publisher"),
            text(doc, "bookImageURL"),
            loanCount
        );
    }

    /** 공백과 하이픈 제거, 13자리 숫자가 아니면 null */
    private static String normalizeIsbn(String isbn) {
        if (isbn == null) {
            return null;
        }
        String normalized = isbn.replaceAll("[\\s-]", "");
        return normalized.matches(ISBN13_PATTERN) ? normalized : null;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asString(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 순위 조회 결과 */
    public record Result(LocalDate sourceDate, List<Item> books) {
    }

    /** 순위 도서 */
    public record Item(
        int rank,
        String isbn13,
        String title,
        String author,
        String publisher,
        String thumbnail,
        /** 대출 건수, 급상승 순위는 null */
        Long loanCount
    ) {
    }
}
