package com.ebook.bookforest.domain.searchkeyword;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class SearchKeywordService {
    static final String PREFIX = "bookforest:search:{ranking}:";
    static final String SNAPSHOT = PREFIX + "popular:24h";
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final RedisScript<Long> RECORD = script("record_search_keyword");
    private static final RedisScript<Long> LIMIT = script("search_request_limit");
    private static final RedisScript<Long> BUILD = script("build_search_keyword_snapshot");
    private static final RedisScript<Long> PUBLISH = script("publish_search_keyword_snapshot");
    private final SearchKeywordProperties config;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final AtomicLong nextWarning = new AtomicLong();
    private LettuceConnectionFactory ownedConnection;

    @Autowired
    public SearchKeywordService(SearchKeywordProperties config, LettuceConnectionFactory connection,
                                ObjectMapper mapper, MeterRegistry metrics) {
        this(config, isolatedTemplate(connection, config), mapper, metrics, Clock.systemUTC());
        ownedConnection = (LettuceConnectionFactory) redis.getConnectionFactory();
    }

    SearchKeywordService(SearchKeywordProperties config, StringRedisTemplate redis,
                         ObjectMapper mapper, MeterRegistry metrics, Clock clock) {
        if (config.getActorSecret() == null || config.getActorSecret().isBlank()) {
            throw new IllegalArgumentException("Search ranking actor secret is required");
        }
        if (config.getRedisTimeout().isNegative() || config.getRedisTimeout().isZero()
            || List.of(config.getMinimumCount(), config.getEventTtlSeconds(), config.getCooldownSeconds(),
                config.getSnapshotTtlSeconds(), config.getBucketTtlSeconds(), config.getActorMinuteLimit(),
                config.getActorHourLimit(), config.getApiActorMinuteLimit(), config.getApiIpMinuteLimit())
                .stream().anyMatch(value -> value < 1)) {
            throw new IllegalArgumentException("Search ranking limits and timeouts must be positive");
        }
        this.config = config;
        this.redis = redis;
        this.mapper = mapper;
        this.metrics = metrics;
        this.clock = clock;
    }

    private static StringRedisTemplate isolatedTemplate(LettuceConnectionFactory existing, SearchKeywordProperties config) {
        var options = ClientOptions.builder().disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .socketOptions(SocketOptions.builder().connectTimeout(config.getRedisTimeout()).build()).build();
        var builder = LettuceClientConfiguration.builder().commandTimeout(config.getRedisTimeout())
            .shutdownTimeout(Duration.ZERO).clientOptions(options);
        if (existing.getClientConfiguration().isUseSsl()) builder.useSsl();
        var factory = existing.getClusterConfiguration() != null
            ? new LettuceConnectionFactory(existing.getClusterConfiguration(), builder.build())
            : existing.getSentinelConfiguration() != null
                ? new LettuceConnectionFactory(existing.getSentinelConfiguration(), builder.build())
                : new LettuceConnectionFactory(existing.getStandaloneConfiguration(), builder.build());
        factory.afterPropertiesSet();
        factory.start();
        return new StringRedisTemplate(factory);
    }

    @PreDestroy
    public void close() { if (ownedConnection != null) ownedConnection.destroy(); }

    private static RedisScript<Long> script(String name) {
        return RedisScript.of(new ClassPathResource("redis/" + name + ".lua"), Long.class);
    }

    public String actorHash(String actor) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.getActorSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("search-ranking:" + actor).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) { throw new IllegalStateException(exception); }
    }

    private static String keywordHash(String keyword) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(keyword.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) { throw new IllegalStateException(exception); }
    }

    public static boolean validEvent(String value) {
        return value != null && value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    public void record(String actor, String eventId, String raw, int page, long resultCount) {
        String keyword = SearchKeywordRules.normalize(raw);
        if (actor == null || page != 0 || !validEvent(eventId) || !SearchKeywordRules.valid(keyword)) return;
        long now = clock.instant().getEpochSecond();
        var time = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        try {
            Long outcome = redis.execute(RECORD, List.of(
                PREFIX + "event:" + actor + ":" + eventId.toLowerCase(java.util.Locale.ROOT),
                PREFIX + "cooldown:" + actor + ":" + keywordHash(keyword),
                PREFIX + "attempt:minute:" + now / 60 + ":" + actor,
                PREFIX + "attempt:hour:" + now / 3600 + ":" + actor,
                PREFIX + "hour:" + HOUR.format(time)),
                String.valueOf(config.getEventTtlSeconds()), String.valueOf(config.getCooldownSeconds()), keyword,
                String.valueOf(config.getBucketTtlSeconds()), String.valueOf((now / 60 + 1) * 60),
                String.valueOf((now / 3600 + 1) * 3600), String.valueOf(config.getActorMinuteLimit()),
                String.valueOf(config.getActorHourLimit()), resultCount > 0 ? "1" : "0");
            metrics.counter("bookforest.search.ranking.events", "outcome", String.valueOf(outcome)).increment();
        } catch (RuntimeException exception) { failure("record", exception); }
    }

    /** Zero means allowed; otherwise seconds until the fixed minute window ends. */
    public long retryAfter(String actor, String ip) {
        long now = clock.instant().getEpochSecond();
        long end = (now / 60 + 1) * 60;
        try {
            Long userAllowed = redis.execute(LIMIT, List.of("bookforest:search:limit:actor:" + actor + ":" + now / 60),
                String.valueOf(end), String.valueOf(config.getApiActorMinuteLimit()));
            Long ipAllowed = redis.execute(LIMIT, List.of("bookforest:search:limit:ip:" + actorHash("ip:" + ip) + ":" + now / 60),
                String.valueOf(end), String.valueOf(config.getApiIpMinuteLimit()));
            if (Long.valueOf(0).equals(userAllowed) || Long.valueOf(0).equals(ipAllowed)) {
                metrics.counter("bookforest.search.requests.limited").increment();
                return end - now;
            }
        } catch (RuntimeException exception) { failure("limit", exception); }
        return 0;
    }

    private Set<String> blocked() {
        return config.getBlockedKeywords().stream().map(SearchKeywordRules::normalize).collect(Collectors.toSet());
    }

    public PopularKeywords get(int limit) {
        try {
            String value = redis.opsForValue().get(SNAPSHOT);
            if (value == null) return PopularKeywords.empty();
            var snapshot = mapper.readValue(value, PopularKeywords.class);
            var blocked = blocked();
            var items = new ArrayList<Item>();
            for (Item item : snapshot.items()) {
                if (SearchKeywordRules.visible(item.keyword(), blocked)) items.add(new Item(items.size() + 1, item.keyword()));
                if (items.size() == limit) break;
            }
            return new PopularKeywords(snapshot.generatedAt(), "PT24H", List.copyOf(items));
        } catch (RuntimeException exception) { failure("read", exception); return PopularKeywords.empty(); }
    }

    @Scheduled(cron = "${app.search-ranking.refresh-cron:0 * * * * *}", zone = "UTC")
    public void refresh() {
        String lock = SNAPSHOT + ":lock";
        String token = UUID.randomUUID().toString();
        String temporary = SNAPSHOT + ":tmp:" + token;
        try {
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lock, token, Duration.ofSeconds(55)))) return;
            var now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
            var keys = new ArrayList<String>(List.of(lock, temporary));
            for (int i = 0; i < 24; i++) keys.add(PREFIX + "hour:" + HOUR.format(now.minusHours(i)));
            if (!Long.valueOf(1).equals(redis.execute(BUILD, keys, token))) return;
            var items = new ArrayList<Item>();
            var blocked = blocked();
            // ponytail: 전체 시간 버킷 합산. 고유 키워드 수가 커지면 집계 비용을 재검토한다.
            for (long offset = 0; items.size() < 20; offset += 100) {
                var batch = redis.opsForZSet().reverseRangeByScore(temporary, config.getMinimumCount(), Double.POSITIVE_INFINITY, offset, 100);
                if (batch == null || batch.isEmpty()) break;
                for (String keyword : batch) {
                    if (SearchKeywordRules.visible(keyword, blocked)) items.add(new Item(items.size() + 1, keyword));
                    if (items.size() == 20) break;
                }
                if (batch.size() < 100) break;
            }
            String json = mapper.writeValueAsString(new PopularKeywords(now, "PT24H", List.copyOf(items)));
            redis.execute(PUBLISH, List.of(lock, SNAPSHOT, temporary), token, json, String.valueOf(config.getSnapshotTtlSeconds()));
        } catch (RuntimeException exception) { failure("refresh", exception); }
        // 실패한 작업의 lock과 임시 키는 짧은 TTL로 정리된다.
    }

    private void failure(String operation, RuntimeException exception) {
        metrics.counter("bookforest.search.ranking.failures", "operation", operation).increment();
        long now = clock.millis();
        long next = nextWarning.get();
        if (now >= next && nextWarning.compareAndSet(next, now + 60_000)) {
            log.warn("Search ranking {} failed: {}", operation, exception.getClass().getSimpleName());
        }
    }

    public record Item(int rank, String keyword) {}
    public record PopularKeywords(OffsetDateTime generatedAt, String window, List<Item> items) {
        static PopularKeywords empty() { return new PopularKeywords(null, "PT24H", List.of()); }
    }
}
