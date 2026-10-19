package com.ebook.bookforest.domain.searchkeyword.service;

import com.ebook.bookforest.domain.searchkeyword.config.SearchKeywordProperties;
import com.ebook.bookforest.domain.searchkeyword.config.SearchKeywordWebConfig;
import com.ebook.bookforest.domain.searchkeyword.controller.PopularSearchKeywordController;
import com.ebook.bookforest.domain.searchkeyword.dto.PopularSearchKeywordResponse;
import com.ebook.bookforest.support.RedisTestContainer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;

/**
 * 인기 검색어 집계와 검색 API 요청 한도 동작 검증
 * Lua 스크립트와 키 만료를 실제로 확인하기 위해 Redis 컨테이너 사용
 */
@Tag("db")
class SearchKeywordServiceIntegrationTest {
    static LettuceConnectionFactory connection;
    static StringRedisTemplate redis;
    SearchKeywordProperties config;
    SearchKeywordService service;
    final JsonMapper mapper = JsonMapper.builder().build();
    final Instant now = Instant.now();
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @BeforeAll
    static void startRedis() {
        connection = RedisTestContainer.connectionFactory();
        redis = new StringRedisTemplate(connection);
    }

    @AfterAll
    static void stopRedis() {
        if (connection != null) connection.destroy();
    }

    @BeforeEach
    void reset() {
        // 테스트 전용 Redis 컨테이너만 비움
        try (var client = connection.getConnection()) { client.serverCommands().flushDb(); }
        config = new SearchKeywordProperties();
        config.setActorSecret("test-only-secret");
        service = new SearchKeywordService(config, redis, mapper, new SimpleMeterRegistry(), clock);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurity() { SecurityContextHolder.clearContext(); }

    String bucket(int hoursAgo) {
        return SearchKeywordService.PREFIX + "hour:" + DateTimeFormatter.ofPattern("yyyyMMddHH")
            .format(OffsetDateTime.ofInstant(now, ZoneOffset.UTC).minusHours(hoursAgo));
    }

    void record(String actor, String event, String word, long results) { service.record(actor, event, word, 0, results); }
    String event() { return UUID.randomUUID().toString(); }

    @Test
    void concurrentDuplicatesAndAlternatingWordsRespectCooldown() throws Exception {
        String id = event();
        var pool = Executors.newFixedThreadPool(8);
        try {
            var jobs = new ArrayList<java.util.concurrent.Callable<Void>>();
            for (int i = 0; i < 16; i++) jobs.add(() -> { record("one", id, "해리 포터", 1); return null; });
            for (var job : pool.invokeAll(jobs)) job.get();
        } finally { pool.shutdownNow(); }
        record("one", event(), "한강", 1);
        String rejected = event();
        record("one", rejected, "해리 포터", 1);
        record("one", event(), "한강", 1);
        assertThat(redis.opsForZSet().score(bucket(0), "해리 포터")).isEqualTo(1d);
        assertThat(redis.opsForZSet().score(bucket(0), "한강")).isEqualTo(1d);
        // Simulate cooldown expiry; the rejected event must still remain processed.
        for (String key : redis.keys(SearchKeywordService.PREFIX + "cooldown:*")) redis.delete(key);
        record("one", rejected, "해리 포터", 1);
        assertThat(redis.opsForZSet().score(bucket(0), "해리 포터")).isEqualTo(1d);
        record("one", event(), "해리 포터", 1);
        assertThat(redis.opsForZSet().score(bucket(0), "해리 포터")).isEqualTo(2d);
        assertThat(redis.getExpire(SearchKeywordService.PREFIX + "event:one:" + id)).isGreaterThan(590);
    }

    @Test
    void emptyResultsMissingHeadersAndPagingNeverIncreaseScores() {
        String id = event();
        record("one", id, "해리 포터", 0);
        record("one", id, "해리 포터", 1);
        record("one", null, "한강", 1);
        record("one", "bad", "한강", 1);
        service.record("one", event(), "한강", 1, 1);
        assertThat(redis.hasKey(bucket(0))).isFalse();
    }

    @Test
    void attemptLimitsCountNewEventsIncludingEmptyResultsButNotRetries() {
        config.setActorMinuteLimit(2);
        String first = event();
        record("one", first, "없는책", 0);
        record("one", first, "없는책", 0);
        record("one", event(), "한강", 1);
        String rejected = event();
        record("one", rejected, "소설", 1);
        assertThat(redis.opsForZSet().score(bucket(0), "한강")).isEqualTo(1d);
        assertThat(redis.opsForZSet().score(bucket(0), "소설")).isNull();
        config.setActorMinuteLimit(100);
        record("one", rejected, "소설", 1);
        assertThat(redis.opsForZSet().score(bucket(0), "소설")).isNull();
        config.setActorHourLimit(3);
        record("one", event(), "책제목", 1);
        assertThat(redis.opsForZSet().score(bucket(0), "책제목")).isNull();
    }

    @Test
    void apiLimitsCountBothActorsAndSharedIps() {
        config.setApiActorMinuteLimit(2);
        config.setApiIpMinuteLimit(3);
        assertThat(service.getRetryAfterSeconds("one", "127.0.0.1")).isEqualTo(0);
        assertThat(service.getRetryAfterSeconds("one", "127.0.0.1")).isEqualTo(0);
        assertThat(service.getRetryAfterSeconds("one", "127.0.0.1")).isGreaterThan(0);
        assertThat(service.getRetryAfterSeconds("two", "127.0.0.1")).isGreaterThan(0);
        assertThat(service.getRetryAfterSeconds("two", "127.0.0.2")).isEqualTo(0);
    }

    @Test
    void snapshotFiltersBeforeLimitingAndUsesOnlyLast24Buckets() {
        var blocked = new ArrayList<String>();
        for (int i = 0; i < 105; i++) {
            String word = "차단" + i;
            blocked.add(word);
            redis.opsForZSet().add(bucket(0), word, 1000);
        }
        config.setBlockedKeywords(blocked);
        redis.opsForZSet().add(bucket(23), "해리 포터", 2);
        redis.opsForZSet().add(bucket(0), "해리 포터", 1);
        redis.opsForZSet().add(bucket(0), "한강", 2);
        redis.opsForZSet().add(bucket(24), "과거도서", 500);
        service.refresh();
        assertThat(service.getPopularKeywords(10).items())
            .isEqualTo(List.of(new PopularSearchKeywordResponse.Item(1, "해리 포터")));
        assertThat(redis.getExpire(SearchKeywordService.SNAPSHOT)).isGreaterThan(170);
        config.setBlockedKeywords(List.of("해리 포터"));
        assertThat(service.getPopularKeywords(10).items()).isEmpty();
    }

    @Test
    void emptySnapshotReplacesOldValueAndMissingSnapshotIsEmpty() {
        redis.opsForZSet().add(bucket(0), "한강", 3);
        service.refresh();
        assertThat(service.getPopularKeywords(10).items()).hasSize(1);
        redis.delete(bucket(0));
        service.refresh();
        assertThat(service.getPopularKeywords(10).generatedAt()).isNotNull();
        assertThat(service.getPopularKeywords(10).items()).isEmpty();
        redis.delete(SearchKeywordService.SNAPSHOT);
        assertThat(service.getPopularKeywords(10).generatedAt()).isNull();
    }

    @Test
    void expiredLockCannotPublishOverNewOwner() {
        var publish = RedisScript.of(new ClassPathResource("redis/publish_search_keyword_snapshot.lua"), Long.class);
        String lock = SearchKeywordService.SNAPSHOT + ":lock";
        redis.opsForValue().set(lock, "new-owner", Duration.ofSeconds(55));
        redis.opsForValue().set(SearchKeywordService.SNAPSHOT, "old-value");
        List<String> keys = List.of(lock, SearchKeywordService.SNAPSHOT, SearchKeywordService.SNAPSHOT + ":tmp:test");
        assertThat(redis.execute(publish, keys, "expired-owner", "bad-value", "180")).isEqualTo(0L);
        assertThat(redis.opsForValue().get(SearchKeywordService.SNAPSHOT)).isEqualTo("old-value");
        assertThat(redis.opsForValue().get(lock)).isEqualTo("new-owner");
    }

    @Test
    void redisFailuresDoNotFailSearchOrKeywordReads() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        given(broken.opsForValue()).willThrow(new org.springframework.dao.QueryTimeoutException("test"));
        willThrow(new org.springframework.dao.QueryTimeoutException("test")).given(broken)
            .execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), org.mockito.ArgumentMatchers.<Object[]>any());
        var unavailable = new SearchKeywordService(config, broken, mapper, new SimpleMeterRegistry(), clock);
        assertThatCode(() -> unavailable.record("one", event(), "한강", 0, 1)).doesNotThrowAnyException();
        assertThat(unavailable.getRetryAfterSeconds("one", "127.0.0.1")).isEqualTo(0);
        assertThat(unavailable.getPopularKeywords(10).items()).isEmpty();
        assertThatCode(unavailable::refresh).doesNotThrowAnyException();
    }

    @Test
    void httpSearchAndPopularEndpointsShareRealRedisAndRespectMetadata() throws Exception {
        var books = mock(com.ebook.bookforest.domain.book.service.BookSearchService.class);
        given(books.searchPlatformBooks(any(), any()))
            .willReturn(new com.ebook.bookforest.domain.book.dto.PlatformBookSearchResponse(1, List.of()));
        var web = new SearchKeywordWebConfig(service, config);
        // Capture the interceptor registration through the same configuration used by MVC.
        var capturingRegistry = mock(org.springframework.web.servlet.config.annotation.InterceptorRegistry.class);
        var captured = org.mockito.ArgumentCaptor.forClass(org.springframework.web.servlet.HandlerInterceptor.class);
        given(capturingRegistry.addInterceptor(captured.capture())).willAnswer(invocation ->
            new org.springframework.web.servlet.config.annotation.InterceptorRegistration(invocation.getArgument(0)));
        web.addInterceptors(capturingRegistry);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
            new com.ebook.bookforest.domain.book.controller.BookController(books, service),
            new PopularSearchKeywordController(service))
            .addMappedInterceptors(new String[]{"/api/book/search", "/api/book/search/platforms"}, captured.getValue()).build();
        String url = "/api/book/search/platforms";
        var cookie = new jakarta.servlet.http.Cookie("bf_anon_id", event());
        String id = event();
        for (int i = 0; i < 2; i++) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .param("keyword", "한강").param("platformType", "ALL").param("page", "0").param("size", "20")
                .cookie(cookie).header("X-Search-Event-Id", id))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
            .param("keyword", "한강").param("platformType", "ALL").param("page", "0").param("size", "20").cookie(cookie))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertThat(redis.opsForZSet().score(bucket(0), "한강")).isEqualTo(1d);
        record("second-user", event(), "한강", 1);
        record("third-user", event(), "한강", 1);
        service.refresh();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/search-keywords/popular"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].keyword").value("한강"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].count").doesNotExist());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/search-keywords/popular?limit=21"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        config.setApiActorMinuteLimit(3);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
            .param("keyword", "한강").param("platformType", "ALL").param("page", "0").param("size", "20").cookie(cookie))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isTooManyRequests())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("Retry-After"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
            .param("keyword", "가").param("platformType", "ALL").param("page", "0").param("size", "20"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        then(books).should(times(3)).searchPlatformBooks(any(), any());
    }

    @Test
    void isolatedConnectionPreservesDatabaseAndDoesNotChangeAuthTimeout() {
        try (var isolated = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            isolated.registerBean(SearchKeywordProperties.class, () -> config);
            isolated.registerBean(LettuceConnectionFactory.class, () ->
                new LettuceConnectionFactory(connection.getStandaloneConfiguration(), connection.getClientConfiguration()));
            isolated.registerBean(tools.jackson.databind.ObjectMapper.class, () -> mapper);
            isolated.registerBean(io.micrometer.core.instrument.MeterRegistry.class, SimpleMeterRegistry::new);
            isolated.register(SearchKeywordService.class);
            Duration oldTimeout = connection.getClientConfiguration().getCommandTimeout();
            isolated.refresh();
            var instance = isolated.getBean(SearchKeywordService.class);
            instance.record("context-user", event(), "테스트", 0, 1);
            assertThat(redis.opsForZSet().score(bucket(0), "테스트")).isEqualTo(1d);
            assertThat(connection.getClientConfiguration().getCommandTimeout()).isEqualTo(oldTimeout);
        }
    }
}
