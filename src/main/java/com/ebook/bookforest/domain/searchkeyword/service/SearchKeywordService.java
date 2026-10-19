package com.ebook.bookforest.domain.searchkeyword.service;

import com.ebook.bookforest.domain.searchkeyword.config.SearchKeywordProperties;
import com.ebook.bookforest.domain.searchkeyword.dto.PopularSearchKeywordResponse;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
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
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 인기 검색어 집계 서비스
 *
 * <p>집계 방식
 * <ul>
 *   <li>검색마다 시간 단위 Redis 정렬 집합에 점수 1 추가, 같은 이벤트와 같은 사용자의 반복 검색은 제외</li>
 *   <li>스케줄러가 최근 24시간 집합을 합쳐 상위 20개 스냅샷을 만들고, 조회 API는 스냅샷만 읽음</li>
 * </ul>
 *
 * <p>Redis 장애가 검색 자체를 막지 않도록 짧은 타임아웃의 전용 연결을 쓰고, 실패는 경고 로그와 지표로만 남김
 */
@Slf4j
@Service
public class SearchKeywordService {

    /** 집계 키 접두어, 클러스터에서 같은 슬롯에 두기 위한 해시 태그 포함 */
    static final String PREFIX = "bookforest:search:{ranking}:";
    static final String SNAPSHOT = PREFIX + "popular:24h";

    private static final String API_LIMIT_PREFIX = "bookforest:search:limit:";
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final Pattern UUID_PATTERN =
        Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final int SNAPSHOT_HOURS = 24;
    private static final int SNAPSHOT_SIZE = 20;
    private static final int SNAPSHOT_BATCH_SIZE = 100;
    /** 스냅샷 생성 락 유지 시간, 매분 실행하는 스케줄보다 짧게 둠 */
    private static final Duration SNAPSHOT_LOCK_TTL = Duration.ofSeconds(55);
    private static final long WARNING_INTERVAL_MILLIS = 60_000;

    private static final RedisScript<Long> RECORD = script("record_search_keyword");
    private static final RedisScript<Long> LIMIT = script("search_request_limit");
    private static final RedisScript<Long> BUILD = script("build_search_keyword_snapshot");
    private static final RedisScript<Long> PUBLISH = script("publish_search_keyword_snapshot");

    private final SearchKeywordProperties properties;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MeterRegistry metrics;
    private final Clock clock;
    /** 다음 경고 로그를 남길 수 있는 시각, 장애 중 로그 폭주 방지 */
    private final AtomicLong nextWarningAt = new AtomicLong();
    private LettuceConnectionFactory ownedConnection;

    /** 운영용 생성자, 기본 Redis 설정을 복사해 짧은 타임아웃의 전용 연결 생성 */
    @Autowired
    public SearchKeywordService(
        SearchKeywordProperties properties,
        LettuceConnectionFactory connection,
        ObjectMapper objectMapper,
        MeterRegistry metrics
    ) {
        this(properties, isolatedTemplate(connection, properties), objectMapper, metrics, Clock.systemUTC());
        this.ownedConnection = (LettuceConnectionFactory) redis.getConnectionFactory();
    }

    /** 테스트용 생성자, Redis 연결과 시계를 직접 지정 */
    SearchKeywordService(
        SearchKeywordProperties properties,
        StringRedisTemplate redis,
        ObjectMapper objectMapper,
        MeterRegistry metrics,
        Clock clock
    ) {
        validateProperties(properties);
        this.properties = properties;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.clock = clock;
    }

    @PreDestroy
    public void close() {
        if (ownedConnection != null) {
            ownedConnection.destroy();
        }
    }

    /** 이벤트 ID와 익명 사용자 쿠키 값의 UUID 형식 여부 */
    public static boolean isUuid(String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }

    /** 사용자 식별값 해시(HMAC-SHA256), Redis 키에 원래 식별값을 남기지 않기 위한 처리 */
    public String hashActor(String actor) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getActorSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(("search-ranking:" + actor).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * 검색어 집계 기록
     *
     * <p>집계하지 않는 경우
     * <ul>
     *   <li>사용자 식별값이 없거나 첫 페이지가 아닌 경우</li>
     *   <li>이벤트 ID가 UUID가 아니거나 정규화한 검색어가 2~100자 규칙에 맞지 않는 경우</li>
     *   <li>같은 이벤트의 재시도, 같은 사용자의 같은 검색어 재검색(쿨다운 안), 사용자별 시도 한도 초과</li>
     *   <li>검색 결과가 0건인 경우(시도 횟수에는 포함)</li>
     * </ul>
     */
    public void record(String actor, String eventId, String rawKeyword, int page, long resultCount) {
        String keyword = SearchKeywordRules.normalize(rawKeyword);
        if (actor == null || page != 0 || !isUuid(eventId) || !SearchKeywordRules.isValid(keyword)) {
            return;
        }

        long nowSeconds = clock.instant().getEpochSecond();
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        try {
            Long outcome = redis.execute(
                RECORD,
                List.of(
                    PREFIX + "event:" + actor + ":" + eventId.toLowerCase(Locale.ROOT),
                    PREFIX + "cooldown:" + actor + ":" + hashKeyword(keyword),
                    PREFIX + "attempt:minute:" + nowSeconds / 60 + ":" + actor,
                    PREFIX + "attempt:hour:" + nowSeconds / 3600 + ":" + actor,
                    hourBucketKey(now)
                ),
                String.valueOf(properties.getEventTtlSeconds()),
                String.valueOf(properties.getCooldownSeconds()),
                keyword,
                String.valueOf(properties.getBucketTtlSeconds()),
                String.valueOf((nowSeconds / 60 + 1) * 60),
                String.valueOf((nowSeconds / 3600 + 1) * 3600),
                String.valueOf(properties.getActorMinuteLimit()),
                String.valueOf(properties.getActorHourLimit()),
                resultCount > 0 ? "1" : "0"
            );
            metrics.counter("bookforest.search.ranking.events", "outcome", String.valueOf(outcome)).increment();
        } catch (RuntimeException exception) {
            handleFailure("record", exception);
        }
    }

    /**
     * 검색 API 요청 한도 확인(사용자별, IP별, 1분 고정 구간)
     *
     * @return 허용이면 0, 한도 초과면 현재 1분 구간이 끝날 때까지 남은 초
     */
    public long getRetryAfterSeconds(String actor, String ip) {
        long nowSeconds = clock.instant().getEpochSecond();
        long windowEnd = (nowSeconds / 60 + 1) * 60;
        try {
            Long actorAllowed = redis.execute(
                LIMIT,
                List.of(API_LIMIT_PREFIX + "actor:" + actor + ":" + nowSeconds / 60),
                String.valueOf(windowEnd),
                String.valueOf(properties.getApiActorMinuteLimit())
            );
            Long ipAllowed = redis.execute(
                LIMIT,
                List.of(API_LIMIT_PREFIX + "ip:" + hashActor("ip:" + ip) + ":" + nowSeconds / 60),
                String.valueOf(windowEnd),
                String.valueOf(properties.getApiIpMinuteLimit())
            );
            if (Long.valueOf(0).equals(actorAllowed) || Long.valueOf(0).equals(ipAllowed)) {
                metrics.counter("bookforest.search.requests.limited").increment();
                return windowEnd - nowSeconds;
            }
        } catch (RuntimeException exception) {
            handleFailure("limit", exception);
        }
        return 0;
    }

    /** 인기 검색어 조회(스냅샷 기준), 조회 시점의 차단어를 다시 적용한 뒤 순위를 다시 매김 */
    public PopularSearchKeywordResponse getPopularKeywords(int limit) {
        try {
            String value = redis.opsForValue().get(SNAPSHOT);
            if (value == null) {
                return PopularSearchKeywordResponse.empty();
            }

            PopularSearchKeywordResponse snapshot = objectMapper.readValue(value, PopularSearchKeywordResponse.class);
            Set<String> blockedKeywords = normalizedBlockedKeywords();
            List<PopularSearchKeywordResponse.Item> items = new ArrayList<>();
            for (PopularSearchKeywordResponse.Item item : snapshot.items()) {
                if (SearchKeywordRules.isVisible(item.keyword(), blockedKeywords)) {
                    items.add(new PopularSearchKeywordResponse.Item(items.size() + 1, item.keyword()));
                }
                if (items.size() == limit) {
                    break;
                }
            }
            return new PopularSearchKeywordResponse(
                snapshot.generatedAt(),
                PopularSearchKeywordResponse.WINDOW_24_HOURS,
                List.copyOf(items)
            );
        } catch (RuntimeException exception) {
            handleFailure("read", exception);
            return PopularSearchKeywordResponse.empty();
        }
    }

    /**
     * 인기 검색어 스냅샷 갱신
     * 여러 서버 중 락을 얻은 한 서버만 최근 24시간 집합을 합쳐 상위 20개를 저장
     * 실패한 작업의 락과 임시 키는 짧은 TTL로 자동 정리
     */
    public void refresh() {
        String lockKey = SNAPSHOT + ":lock";
        String token = UUID.randomUUID().toString();
        String temporaryKey = SNAPSHOT + ":tmp:" + token;
        try {
            boolean locked = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, token, SNAPSHOT_LOCK_TTL));
            if (!locked) {
                return;
            }

            OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
            List<String> keys = new ArrayList<>(List.of(lockKey, temporaryKey));
            for (int hoursAgo = 0; hoursAgo < SNAPSHOT_HOURS; hoursAgo++) {
                keys.add(hourBucketKey(now.minusHours(hoursAgo)));
            }
            if (!Long.valueOf(1).equals(redis.execute(BUILD, keys, token))) {
                return;
            }

            List<PopularSearchKeywordResponse.Item> items = collectTopKeywords(temporaryKey);
            String json = objectMapper.writeValueAsString(
                new PopularSearchKeywordResponse(now, PopularSearchKeywordResponse.WINDOW_24_HOURS, List.copyOf(items))
            );
            redis.execute(
                PUBLISH,
                List.of(lockKey, SNAPSHOT, temporaryKey),
                token,
                json,
                String.valueOf(properties.getSnapshotTtlSeconds())
            );
        } catch (RuntimeException exception) {
            handleFailure("refresh", exception);
        }
    }

    /**
     * 합산 집합에서 노출 가능한 상위 검색어를 순서대로 수집
     * 차단어를 먼저 거르고 20개를 채우므로 100개씩 나눠 읽음
     * 고유 검색어 수가 크게 늘면 전체 시간 버킷 합산 비용 재검토 필요
     */
    private List<PopularSearchKeywordResponse.Item> collectTopKeywords(String temporaryKey) {
        List<PopularSearchKeywordResponse.Item> items = new ArrayList<>();
        Set<String> blockedKeywords = normalizedBlockedKeywords();

        for (long offset = 0; items.size() < SNAPSHOT_SIZE; offset += SNAPSHOT_BATCH_SIZE) {
            Set<String> batch = redis.opsForZSet().reverseRangeByScore(
                temporaryKey,
                properties.getMinimumCount(),
                Double.POSITIVE_INFINITY,
                offset,
                SNAPSHOT_BATCH_SIZE
            );
            if (batch == null || batch.isEmpty()) {
                break;
            }

            for (String keyword : batch) {
                if (SearchKeywordRules.isVisible(keyword, blockedKeywords)) {
                    items.add(new PopularSearchKeywordResponse.Item(items.size() + 1, keyword));
                }
                if (items.size() == SNAPSHOT_SIZE) {
                    break;
                }
            }
            if (batch.size() < SNAPSHOT_BATCH_SIZE) {
                break;
            }
        }
        return items;
    }

    private Set<String> normalizedBlockedKeywords() {
        return properties.getBlockedKeywords().stream()
            .map(SearchKeywordRules::normalize)
            .collect(Collectors.toSet());
    }

    private static String hourBucketKey(OffsetDateTime time) {
        return PREFIX + "hour:" + HOUR.format(time);
    }

    private static String hashKeyword(String keyword) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(keyword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * 실패 지표는 매번, 경고 로그는 1분에 한 번만 기록
     *
     * @param operation 지표 태그 값(record, limit, read, refresh), 모니터링 조회와 맞물려 변경 금지
     */
    private void handleFailure(String operation, RuntimeException exception) {
        metrics.counter("bookforest.search.ranking.failures", "operation", operation).increment();

        long now = clock.millis();
        long next = nextWarningAt.get();
        if (now >= next && nextWarningAt.compareAndSet(next, now + WARNING_INTERVAL_MILLIS)) {
            log.warn("인기 검색어 처리 실패: operation={}, exceptionType={}", operation, exception.getClass().getSimpleName());
        }
    }

    private static void validateProperties(SearchKeywordProperties properties) {
        if (properties.getActorSecret() == null || properties.getActorSecret().isBlank()) {
            throw new IllegalArgumentException("인기 검색어 사용자 식별 비밀값은 필수입니다.");
        }

        boolean invalidTimeout = properties.getRedisTimeout().isNegative() || properties.getRedisTimeout().isZero();
        boolean invalidLimit = List.of(
                properties.getMinimumCount(),
                properties.getEventTtlSeconds(),
                properties.getCooldownSeconds(),
                properties.getSnapshotTtlSeconds(),
                properties.getBucketTtlSeconds(),
                properties.getActorMinuteLimit(),
                properties.getActorHourLimit(),
                properties.getApiActorMinuteLimit(),
                properties.getApiIpMinuteLimit()
            )
            .stream()
            .anyMatch(value -> value < 1);
        if (invalidTimeout || invalidLimit) {
            throw new IllegalArgumentException("인기 검색어 한도와 타임아웃은 양수여야 합니다.");
        }
    }

    /**
     * 기본 Redis 설정을 복사한 전용 연결 생성
     * 인기 검색어 장애가 다른 기능의 Redis 연결 설정에 영향을 주지 않도록 별도 타임아웃 적용
     */
    private static StringRedisTemplate isolatedTemplate(
        LettuceConnectionFactory existing,
        SearchKeywordProperties properties
    ) {
        ClientOptions options = ClientOptions.builder()
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .socketOptions(SocketOptions.builder().connectTimeout(properties.getRedisTimeout()).build())
            .build();
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder()
            .commandTimeout(properties.getRedisTimeout())
            .shutdownTimeout(Duration.ZERO)
            .clientOptions(options);
        if (existing.getClientConfiguration().isUseSsl()) {
            builder.useSsl();
        }

        LettuceConnectionFactory factory;
        if (existing.getClusterConfiguration() != null) {
            factory = new LettuceConnectionFactory(existing.getClusterConfiguration(), builder.build());
        } else if (existing.getSentinelConfiguration() != null) {
            factory = new LettuceConnectionFactory(existing.getSentinelConfiguration(), builder.build());
        } else {
            factory = new LettuceConnectionFactory(existing.getStandaloneConfiguration(), builder.build());
        }
        factory.afterPropertiesSet();
        factory.start();
        return new StringRedisTemplate(factory);
    }

    private static RedisScript<Long> script(String name) {
        return RedisScript.of(new ClassPathResource("redis/" + name + ".lua"), Long.class);
    }
}
