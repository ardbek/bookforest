package com.ebook.bookforest.domain.searchkeyword;

import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
import com.ebook.bookforest.domain.user.enums.UserRole;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.ServerSocket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.ForwardedHeaderFilter;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchKeywordTest {
    static Process server;
    static LettuceConnectionFactory connection;
    static StringRedisTemplate redis;
    SearchKeywordProperties config;
    SearchKeywordService service;
    final JsonMapper mapper = JsonMapper.builder().build();
    final Instant now = Instant.now();
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @BeforeAll
    static void startRedis() throws Exception {
        String binary = System.getenv().getOrDefault("REDIS_SERVER", "redis-server");
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        try {
            server = new ProcessBuilder(binary, "--bind", "127.0.0.1", "--port", String.valueOf(port),
                "--save", "", "--appendonly", "no")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (java.io.IOException missing) {
            Assumptions.abort("Install redis-server or set REDIS_SERVER to run isolated Redis integration tests");
        }
        connection = new LettuceConnectionFactory("127.0.0.1", port);
        connection.afterPropertiesSet();
        connection.start();
        redis = new StringRedisTemplate(connection);
        for (int i = 0; i < 30; i++) {
            try (var client = connection.getConnection()) { if ("PONG".equals(client.ping())) return; }
            catch (RuntimeException starting) { Thread.sleep(50); }
        }
        fail("Isolated test Redis did not start");
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (connection != null) connection.destroy();
        if (server != null) { server.destroy(); server.waitFor(); }
    }

    @BeforeEach
    void reset() {
        // Only the disposable Redis process above is cleared.
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
    void normalizesWithoutTruncatingAndDoesNotBlockIsbn() {
        assertEquals("harry potter", SearchKeywordRules.normalize("  Ｈarry\t\n Potter\u0000  "));
        assertFalse(SearchKeywordRules.valid("가"));
        assertFalse(SearchKeywordRules.valid("!?!"));
        assertFalse(SearchKeywordRules.valid("가".repeat(101)));
        assertTrue(SearchKeywordRules.visible("9781234567890", Set.of()));
        assertFalse(SearchKeywordRules.visible("전화 010-1234-5678", Set.of()));
        assertFalse(SearchKeywordRules.visible("a@example.com", Set.of()));
        assertTrue(SearchKeywordRules.visible("금지된 사랑", Set.of("사랑")));
    }

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
        assertEquals(1d, redis.opsForZSet().score(bucket(0), "해리 포터"));
        assertEquals(1d, redis.opsForZSet().score(bucket(0), "한강"));
        // Simulate cooldown expiry; the rejected event must still remain processed.
        for (String key : redis.keys(SearchKeywordService.PREFIX + "cooldown:*")) redis.delete(key);
        record("one", rejected, "해리 포터", 1);
        assertEquals(1d, redis.opsForZSet().score(bucket(0), "해리 포터"));
        record("one", event(), "해리 포터", 1);
        assertEquals(2d, redis.opsForZSet().score(bucket(0), "해리 포터"));
        assertTrue(redis.getExpire(SearchKeywordService.PREFIX + "event:one:" + id) > 590);
    }

    @Test
    void emptyResultsMissingHeadersAndPagingNeverIncreaseScores() {
        String id = event();
        record("one", id, "해리 포터", 0);
        record("one", id, "해리 포터", 1);
        record("one", null, "한강", 1);
        record("one", "bad", "한강", 1);
        service.record("one", event(), "한강", 1, 1);
        assertFalse(redis.hasKey(bucket(0)));
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
        assertEquals(1d, redis.opsForZSet().score(bucket(0), "한강"));
        assertNull(redis.opsForZSet().score(bucket(0), "소설"));
        config.setActorMinuteLimit(100);
        record("one", rejected, "소설", 1);
        assertNull(redis.opsForZSet().score(bucket(0), "소설"));
        config.setActorHourLimit(3);
        record("one", event(), "책제목", 1);
        assertNull(redis.opsForZSet().score(bucket(0), "책제목"));
    }

    @Test
    void apiLimitsCountBothActorsAndSharedIps() {
        config.setApiActorMinuteLimit(2);
        config.setApiIpMinuteLimit(3);
        assertEquals(0, service.retryAfter("one", "127.0.0.1"));
        assertEquals(0, service.retryAfter("one", "127.0.0.1"));
        assertTrue(service.retryAfter("one", "127.0.0.1") > 0);
        assertTrue(service.retryAfter("two", "127.0.0.1") > 0);
        assertEquals(0, service.retryAfter("two", "127.0.0.2"));
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
        assertEquals(List.of(new SearchKeywordService.Item(1, "해리 포터")), service.get(10).items());
        assertTrue(redis.getExpire(SearchKeywordService.SNAPSHOT) > 170);
        config.setBlockedKeywords(List.of("해리 포터"));
        assertTrue(service.get(10).items().isEmpty());
    }

    @Test
    void emptySnapshotReplacesOldValueAndMissingSnapshotIsEmpty() {
        redis.opsForZSet().add(bucket(0), "한강", 3);
        service.refresh();
        assertEquals(1, service.get(10).items().size());
        redis.delete(bucket(0));
        service.refresh();
        assertNotNull(service.get(10).generatedAt());
        assertTrue(service.get(10).items().isEmpty());
        redis.delete(SearchKeywordService.SNAPSHOT);
        assertNull(service.get(10).generatedAt());
    }

    @Test
    void expiredLockCannotPublishOverNewOwner() {
        var publish = RedisScript.of(new ClassPathResource("redis/publish_search_keyword_snapshot.lua"), Long.class);
        String lock = SearchKeywordService.SNAPSHOT + ":lock";
        redis.opsForValue().set(lock, "new-owner", Duration.ofSeconds(55));
        redis.opsForValue().set(SearchKeywordService.SNAPSHOT, "old-value");
        assertEquals(0L, redis.execute(publish, List.of(lock, SearchKeywordService.SNAPSHOT, SearchKeywordService.SNAPSHOT + ":tmp:test"),
            "expired-owner", "bad-value", "180"));
        assertEquals("old-value", redis.opsForValue().get(SearchKeywordService.SNAPSHOT));
        assertEquals("new-owner", redis.opsForValue().get(lock));
    }

    @Test
    void redisFailuresDoNotFailSearchOrKeywordReads() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.opsForValue()).thenThrow(new org.springframework.dao.QueryTimeoutException("test"));
        doThrow(new org.springframework.dao.QueryTimeoutException("test")).when(broken)
            .execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), org.mockito.ArgumentMatchers.<Object[]>any());
        var unavailable = new SearchKeywordService(config, broken, mapper, new SimpleMeterRegistry(), clock);
        assertDoesNotThrow(() -> unavailable.record("one", event(), "한강", 0, 1));
        assertEquals(0, unavailable.retryAfter("one", "127.0.0.1"));
        assertTrue(unavailable.get(10).items().isEmpty());
        assertDoesNotThrow(unavailable::refresh);
    }

    @Test
    void actorCookiesAndAuthenticatedUsersAreResolvedServerSide() {
        var web = new SearchKeywordWebConfig(service, config);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        String anonymous = web.resolveActor(request, response);
        assertFalse(anonymous.contains("anonymous"));
        assertTrue(response.getHeader("Set-Cookie").contains("HttpOnly"));
        assertTrue(response.getHeader("Set-Cookie").contains("Secure"));
        String cookieValue = response.getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1];
        request.setCookies(new jakarta.servlet.http.Cookie("bf_anon_id", cookieValue));
        assertEquals(anonymous, web.resolveActor(request, new MockHttpServletResponse()));
        var principal = new JwtPrincipal(42L, UserRole.values()[0]);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        assertEquals(service.actorHash("user:42"), web.resolveActor(request, response));
    }

    @Test
    void forwardedHeadersOnlyAffectLimitsForConfiguredProxyPeers() throws Exception {
        var web = new SearchKeywordWebConfig(service, config);
        var request = new MockHttpServletRequest("GET", "/api/book/search/platforms");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.2");
        assertEquals("10.0.0.1", web.clientIp(request));
        config.setTrustedProxies(List.of("10.0.0.1", "10.0.0.2"));
        new ForwardedHeaderFilter().doFilter(request, new MockHttpServletResponse(), (wrapped, response) ->
            assertEquals("1.2.3.4", web.clientIp((jakarta.servlet.http.HttpServletRequest) wrapped)));
    }

    @Test
    void httpSearchAndPopularEndpointsShareRealRedisAndRespectMetadata() throws Exception {
        var books = mock(com.ebook.bookforest.domain.book.service.PlatformBookService.class);
        when(books.searchPlatformBooks(anyString(), any(), any(), anyString(), any(), any(), anyInt(), anyInt(), any()))
            .thenReturn(new com.ebook.bookforest.domain.book.dto.PlatformBookSearchResponse(1, List.of()));
        var web = new SearchKeywordWebConfig(service, config);
        // Capture the interceptor registration through the same configuration used by MVC.
        var capturingRegistry = mock(org.springframework.web.servlet.config.annotation.InterceptorRegistry.class);
        var captured = org.mockito.ArgumentCaptor.forClass(org.springframework.web.servlet.HandlerInterceptor.class);
        when(capturingRegistry.addInterceptor(captured.capture())).thenAnswer(invocation ->
            new org.springframework.web.servlet.config.annotation.InterceptorRegistration(invocation.getArgument(0)));
        web.addInterceptors(capturingRegistry);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
            new com.ebook.bookforest.domain.book.controller.BookController(
                mock(com.ebook.bookforest.domain.book.service.BookSearchService.class), books, service),
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
        assertEquals(1d, redis.opsForZSet().score(bucket(0), "한강"));
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
        verify(books, times(3)).searchPlatformBooks(anyString(), any(), any(), anyString(), any(), any(), anyInt(), anyInt(), any());
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
            assertEquals(1d, redis.opsForZSet().score(bucket(0), "테스트"));
            assertEquals(oldTimeout, connection.getClientConfiguration().getCommandTimeout());
        }
    }
}
