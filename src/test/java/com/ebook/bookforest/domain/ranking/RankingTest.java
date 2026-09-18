package com.ebook.bookforest.domain.ranking;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.repository.BookPlatformSummary;
import com.ebook.bookforest.domain.book.repository.BookRepository;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RankingTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final LocalDate today = LocalDate.of(2026, 9, 17);
    private final DataLibraryClient client = mock(DataLibraryClient.class);
    private final BookRepository books = mock(BookRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Map<String, String> cache = new HashMap<>();
    private final RankingService service = new RankingService(client, books, redis, mapper);

    RankingTest() {
        when(client.isConfigured()).thenReturn(true);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.hasKey(anyString())).thenAnswer(call -> cache.containsKey(call.getArgument(0)));
        when(values.get(anyString())).thenAnswer(call -> cache.get(call.getArgument(0)));
        doAnswer(call -> { cache.put(call.getArgument(0), call.getArgument(1)); return null; })
            .when(values).set(anyString(), anyString());
    }

    @Test
    void popularParsesProviderRankLoansAndMissingIsbn() {
        var response = DataLibraryClient.parse(mapper.readTree("""
            {"response":{"docs":[
              {"doc":{"ranking":"3","isbn13":"","bookname":"ISBN 없는 책","authors":"저자","loan_count":"20"}},
              {"doc":{"ranking":"1","isbn13":"978-1234567890","bookname":"책","authors":"저자","loan_count":"500"}},
              {"doc":{"ranking":"2","isbn13":"invalid","bookname":"ISBN 오류 도서","loan_count":"100"}}
            ]}}
            """), RankingType.POPULAR, today);
        assertEquals(today, response.sourceDate());
        assertEquals(3, response.books().size());
        assertEquals(1, response.books().get(0).rank());
        assertEquals("9781234567890", response.books().get(0).isbn13());
        assertEquals(500L, response.books().get(0).loanCount());
        assertNull(response.books().get(1).isbn13());
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
        assertEquals(today, response.sourceDate());
        assertEquals(List.of("9780000000003", "9780000000002"), response.books().stream()
            .map(DataLibraryClient.Item::isbn13).toList());
        assertNull(response.books().get(0).loanCount());
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
        assertEquals(today.minusDays(1), response.sourceDate());
        assertEquals("9780000000002", response.books().get(0).isbn13());
    }

    @Test
    void allEmptyTrendDaysKeepExistingCache() {
        String previous = mapper.writeValueAsString(new RankingResponse(null, today.minusDays(1), List.of(
            new RankingResponse.Book(1, 1L, "9780000000001", "이전 도서", "저자", null, null, null, List.of()))));
        cache.put(RankingType.TRENDING.cacheKey(), previous);
        when(client.fetch(eq(RankingType.TRENDING), any())).thenAnswer(call -> DataLibraryClient.parse(
            mapper.readTree("""
                {"response":{"results":[
                  {"result":{"date":"2026-09-17","docs":[]}},
                  {"result":{"date":"2026-09-16","docs":[]}}
                ]}}
                """), RankingType.TRENDING, today));
        service.refresh(RankingType.TRENDING);
        assertEquals(previous, cache.get(RankingType.TRENDING.cacheKey()));
        verify(values, never()).set(anyString(), anyString());
        verifyNoInteractions(books);
    }

    @Test
    void rejectsProviderErrorsAndMalformedPayloads() {
        for (String json : List.of("null", "{}", "{\"response\":{\"error\":\"invalid key\"}}",
            "{\"response\":{\"docs\":{}}}", "{\"response\":{\"docs\":[{}]}}")) {
            assertThrows(RuntimeException.class, () -> DataLibraryClient.parse(mapper.readTree(json), RankingType.POPULAR, today));
        }
        assertThrows(RuntimeException.class, () -> DataLibraryClient.parse(
            mapper.readTree("{\"response\":{\"results\":[]}}"), RankingType.TRENDING, today));
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
            assertEquals("/api/loanItemSrch?authKey=test-key&format=json&startDt=2026-08-18&endDt=2026-09-17&pageNo=1&pageSize=50", requests.get(0));
            assertEquals("/api/hotTrend?authKey=test-key&format=json&searchDt=2026-09-17", requests.get(1));
            var slowClient = new DataLibraryClient(RestClient.builder(), mapper, "test-key", base + "/slow", Duration.ofMillis(50));
            assertThrows(RuntimeException.class, () -> slowClient.fetch(RankingType.POPULAR, today));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retainsAllProviderBooksAndMetadataWhileAddingMatchedPlatforms() {
        var library = Platform.builder().id(1L).platformName("도서관").supportsLibrary(true).build();
        var subscription = Platform.builder().id(2L).platformName("구독").supportsSubscription(true).build();
        var first = BookOrigin.builder().id(10L).platform(library).isbn13Raw("9781234567890")
            .titleRaw("DB 도서명").authorRaw("DB 저자").publisherRaw("DB 출판사").coverImgUrl("/cover.jpg").build();
        var second = BookOrigin.builder().id(11L).platform(subscription).isbn13Raw("9781234567890").build();
        var duplicate = BookOrigin.builder().id(12L).platform(library).isbn13Raw("9781234567890").build();
        when(books.findAllByIsbn13RawInAndActiveTrueOrderByIdAsc(anyList())).thenReturn(List.of(first, second, duplicate));
        when(books.findLibraryPlatformSummaries(anyList())).thenReturn(List.of(
            new BookPlatformSummary(10L, 1L, "LIB", "도서관", 4), new BookPlatformSummary(12L, 1L, "LIB", "도서관", 2)));
        when(books.findSubscriptionPlatformSummaries(anyList())).thenReturn(List.of(new BookPlatformSummary(11L, 2L, "SUB", "구독", 1)));
        when(client.fetch(eq(RankingType.POPULAR), any())).thenReturn(new DataLibraryClient.Result(today, List.of(
            item(1, "9781234567890"), item(2, "9781234567890"), item(3, "9789999999999"), item(4, null))));

        service.refresh(RankingType.POPULAR);
        var response = service.get(RankingType.POPULAR);
        assertNotNull(response.updatedAt());
        assertEquals(today, response.sourceDate());
        assertEquals(4, response.books().size());
        var book = response.books().get(0);
        assertEquals(10L, book.bookId());
        assertEquals("제공 도서1", book.title());
        assertEquals("제공 저자", book.author());
        assertEquals("/source.jpg", book.thumbnail());
        assertEquals(2, book.platforms().size());
        assertEquals(List.of("LIBRARY", "SUBSCRIPTION"), book.platforms().stream().map(RankingResponse.Platform::platformType).toList());
        assertEquals(2, response.books().get(1).rank());
        assertNull(response.books().get(2).bookId());
        assertEquals("9789999999999", response.books().get(2).isbn13());
        assertEquals("제공 도서3", response.books().get(2).title());
        assertTrue(response.books().get(2).platforms().isEmpty());
        assertNull(response.books().get(3).bookId());
        assertEquals(4, response.books().get(3).rank());
        assertEquals("제공 도서4", response.books().get(3).title());
        verify(values).set(eq(RankingType.POPULAR.cacheKey()), anyString());
        verify(values).get(RankingType.POPULAR.cacheKey());
        verifyNoMoreInteractions(values);
    }

    @Test
    void retainsAllFiftyBooksWithoutTruncation() {
        var source = new ArrayList<DataLibraryClient.Item>();
        for (int i = 1; i <= 50; i++) source.add(item(i, null));
        when(client.fetch(eq(RankingType.POPULAR), any())).thenReturn(new DataLibraryClient.Result(today, source));
        service.refresh(RankingType.POPULAR);
        assertEquals(50, service.get(RankingType.POPULAR).books().size());
        verifyNoInteractions(books);
    }

    @Test
    void readsOnlyRedisAndKeepsPreviousSnapshotOnRefreshFailure() {
        String previous = mapper.writeValueAsString(RankingResponse.empty());
        cache.put(RankingType.POPULAR.cacheKey(), previous);
        assertTrue(service.get(RankingType.POPULAR).books().isEmpty());
        verifyNoInteractions(client, books);
        when(client.fetch(eq(RankingType.POPULAR), any())).thenThrow(new IllegalArgumentException("parse failed"));
        service.refresh(RankingType.POPULAR);
        assertEquals(previous, cache.get(RankingType.POPULAR.cacheKey()));
        verify(values, never()).set(anyString(), anyString());
        verify(redis, never()).delete(anyString());
    }

    @Test
    void initializesOnlyMissingCacheAndRefreshesListsIndependently() {
        cache.put(RankingType.POPULAR.cacheKey(), mapper.writeValueAsString(RankingResponse.empty()));
        when(client.fetch(eq(RankingType.TRENDING), any())).thenReturn(new DataLibraryClient.Result(today, List.of(item(1, null))));
        service.initialize();
        verify(client, never()).fetch(eq(RankingType.POPULAR), any());
        assertEquals(1, service.get(RankingType.TRENDING).books().size());
        when(client.fetch(eq(RankingType.POPULAR), any())).thenThrow(new IllegalStateException("upstream failed"));
        new RankingScheduler(service).refresh();
        verify(client, times(2)).fetch(eq(RankingType.TRENDING), any());
        verify(values, never()).set(eq(RankingType.POPULAR.cacheKey()), anyString());
    }

    @Test
    void missingCacheRedisFailureAndAbsentKeyAreSafe() {
        assertEquals(RankingResponse.empty(), service.get(RankingType.POPULAR));
        when(values.get(anyString())).thenThrow(new IllegalStateException("Redis unavailable"));
        assertEquals(RankingResponse.empty(), service.get(RankingType.POPULAR));
        when(client.isConfigured()).thenReturn(false);
        service.initialize();
        service.refresh(RankingType.POPULAR);
        verify(client, never()).fetch(any(), any());
        verifyNoInteractions(books);
    }

    private DataLibraryClient.Item item(int rank, String isbn) {
        return new DataLibraryClient.Item(rank, isbn, "제공 도서" + rank, "제공 저자", "출판사", "/source.jpg", 20L);
    }
}
