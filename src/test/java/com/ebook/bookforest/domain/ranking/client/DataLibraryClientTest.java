package com.ebook.bookforest.domain.ranking.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebook.bookforest.domain.ranking.enums.RankingType;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;


class DataLibraryClientTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final LocalDate today = LocalDate.of(2026, 9, 17);

    @Test
    void popularParsesProviderRankLoansAndMissingIsbn() {
        var response = DataLibraryClient.parse(mapper.readTree("""
            {"response":{"docs":[
              {"doc":{"ranking":"3","isbn13":"","bookname":"ISBN 없는 책","authors":"저자","loan_count":"20"}},
              {"doc":{"ranking":"1","isbn13":"978-1234567890","bookname":"책","authors":"저자","loan_count":"500"}},
              {"doc":{"ranking":"2","isbn13":"invalid","bookname":"ISBN 오류 도서","loan_count":"100"}}
            ]}}
            """), RankingType.POPULAR, today);
        assertThat(response.sourceDate()).isEqualTo(today);
        assertThat(response.books()).hasSize(3);
        assertThat(response.books().get(0).rank()).isEqualTo(1);
        assertThat(response.books().get(0).isbn13()).isEqualTo("9781234567890");
        assertThat(response.books().get(0).loanCount()).isEqualTo(500L);
        assertThat(response.books().get(1).isbn13()).isNull();
    }

    @Test
    void trendingUsesLatestDateAndProviderOrderWithoutInventingLoanCounts() {
        var response = DataLibraryClient.parse(mapper.readTree("""
            {"response":{"results":[
              {"result":{"date":"2026-09-15","docs":[{"doc":{"no":"1","bookname":"도서","isbn13":"9780000000001"}}]}},
              {"result":{"date":"2026-09-17","docs":[{"doc":{"no":"2","bookname":"도서","isbn13":"9780000000002"}},{"doc":{"no":"1","bookname":"도서","isbn13":"9780000000003"}}]}},
              {"result":{"date":"2026-09-16","docs":[{"doc":{"no":"1","bookname":"도서","isbn13":"9780000000004"}}]}}
            ]}}
            """), RankingType.TRENDING, today);
        assertThat(response.sourceDate()).isEqualTo(today);
        assertThat(response.books().stream().map(DataLibraryClient.Item::isbn13).toList())
            .isEqualTo(List.of("9780000000003", "9780000000002"));
        assertThat(response.books().get(0).loanCount()).isNull();
    }

    @Test
    void trendingSkipsEmptyDaysAndUsesLatestPopulatedDate() {
        var response = DataLibraryClient.parse(mapper.readTree("""
            {"response":{"results":[
              {"result":{"date":"2026-09-17","docs":[]}},
              {"result":{"date":"2026-09-15","docs":[{"doc":{"no":"1","bookname":"도서","isbn13":"9780000000001"}}]}},
              {"result":{"date":"2026-09-16","docs":[{"doc":{"no":"1","bookname":"도서","isbn13":"9780000000002"}}]}},
              {"result":{"date":"2026-09-18","docs":[{"doc":{"no":"1","bookname":"도서","isbn13":"9780000000003"}}]}}
            ]}}
            """), RankingType.TRENDING, today);
        assertThat(response.sourceDate()).isEqualTo(today.minusDays(1));
        assertThat(response.books().get(0).isbn13()).isEqualTo("9780000000002");
    }

    @Test
    void rejectsProviderErrorsAndMalformedPayloads() {
        for (String json : List.of("null", "{}", "{\"response\":{\"error\":\"invalid key\"}}",
            "{\"response\":{\"docs\":{}}}", "{\"response\":{\"docs\":[{}]}}")) {
            assertThatThrownBy(() -> DataLibraryClient.parse(mapper.readTree(json), RankingType.POPULAR, today))
                .isInstanceOf(RuntimeException.class);
        }
        assertThatThrownBy(() -> DataLibraryClient.parse(
            mapper.readTree("{\"response\":{\"results\":[]}}"), RankingType.TRENDING, today))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void requestsOnlyGlobalDateAndPagingParametersAndTimesOut() throws Exception {
        var requests = new ArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            String json = exchange.getRequestURI().getPath().endsWith("loanItemSrch")
                ? "{\"response\":{\"docs\":[]}}"
                : "{\"response\":{\"results\":[{\"result\":{\"date\":\"2026-09-17\",\"docs\":[{\"doc\":{\"no\":\"1\",\"bookname\":\"도서\",\"isbn13\":\"9780000000001\"}}]}}]}}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try { Thread.sleep(250); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var actualClient = new DataLibraryClient(RestClient.builder(), mapper, "test-key", base + "/api", Duration.ofSeconds(2));
            actualClient.fetch(RankingType.POPULAR, today);
            actualClient.fetch(RankingType.TRENDING, today);
            assertThat(requests.get(0)).isEqualTo(
                "/api/loanItemSrch?authKey=test-key&format=json&startDt=2026-08-18&endDt=2026-09-17&pageNo=1&pageSize=50"
            );
            assertThat(requests.get(1)).isEqualTo("/api/hotTrend?authKey=test-key&format=json&searchDt=2026-09-17");
            var slowClient = new DataLibraryClient(RestClient.builder(), mapper, "test-key", base + "/slow", Duration.ofMillis(50));
            assertThatThrownBy(() -> slowClient.fetch(RankingType.POPULAR, today)).isInstanceOf(RuntimeException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void trendingWithOnlyEmptyDaysIsRejected() {
        assertThatThrownBy(() -> DataLibraryClient.parse(mapper.readTree("""
            {"response":{"results":[
              {"result":{"date":"2026-09-17","docs":[]}},
              {"result":{"date":"2026-09-16","docs":[]}}
            ]}}
            """), RankingType.TRENDING, today)).isInstanceOf(IllegalArgumentException.class);
    }
}
