package com.ebook.bookforest.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebook.bookforest.domain.auth.model.RefreshTokenRotationCommand;
import com.ebook.bookforest.domain.auth.model.RefreshTokenRotationStatus;
import com.ebook.bookforest.domain.auth.model.RefreshTokenSession;
import com.ebook.bookforest.support.RedisTestContainer;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Refresh Token 세션 저장소 검증
 * Redis 키 형식, Hash 필드, Lua 교체 스크립트 결과는 운영 Redis 데이터와 맞물려 있어 실제 Redis 컨테이너로 확인
 */
@Tag("db")
class RedisRefreshTokenStoreIntegrationTest {

    private static final Long USER_ID = 7L;
    private static final String SESSION_ID = "session-1";
    private static final String SESSION_KEY = "bookforest:auth:refresh:{7}:session:session-1";
    private static final String SESSIONS_KEY = "bookforest:auth:refresh:{7}:sessions";

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private RefreshTokenStore refreshTokenStore;

    @BeforeAll
    static void startRedis() {
        connectionFactory = RedisTestContainer.connectionFactory();
        redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
        refreshTokenStore = new RedisRefreshTokenStore(redisTemplate);
    }

    @Test
    @DisplayName("세션 저장 시 기존 키 형식과 Hash 필드로 저장하고 만료 시간 설정")
    void saveKeepsKeyFormatAndFields() {
        // given
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);

        // when
        refreshTokenStore.save(RefreshTokenSession.initial(SESSION_ID, USER_ID, "token-1", "hash-1", expiresAt));

        // then
        Map<Object, Object> values = redisTemplate.opsForHash().entries(SESSION_KEY);
        assertThat(values).containsOnly(
            Map.entry("userId", "7"),
            Map.entry("currentTokenId", "token-1"),
            Map.entry("currentTokenHash", "hash-1"),
            Map.entry("expiresAt", String.valueOf(expiresAt.toEpochMilli()))
        );
        assertThat(redisTemplate.opsForSet().members(SESSIONS_KEY)).containsExactly(SESSION_ID);
        assertThat(redisTemplate.getExpire(SESSION_KEY)).isPositive();
        assertThat(redisTemplate.getExpire(SESSIONS_KEY)).isPositive();
    }

    @Test
    @DisplayName("이미 만료된 세션은 저장하지 않음")
    void saveIgnoresExpiredSession() {
        // when
        refreshTokenStore.save(RefreshTokenSession.initial(
            SESSION_ID,
            USER_ID,
            "token-1",
            "hash-1",
            Instant.now().minusSeconds(1)
        ));

        // then
        assertThat(redisTemplate.hasKey(SESSION_KEY)).isFalse();
        assertThat(redisTemplate.hasKey(SESSIONS_KEY)).isFalse();
    }

    @Test
    @DisplayName("저장한 세션 조회, 없는 세션은 빈 값")
    void findReturnsSavedSession() {
        // given
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        refreshTokenStore.save(RefreshTokenSession.initial(SESSION_ID, USER_ID, "token-1", "hash-1", expiresAt));

        // when, then
        assertThat(refreshTokenStore.find(USER_ID, SESSION_ID)).contains(
            new RefreshTokenSession(SESSION_ID, USER_ID, "token-1", "hash-1", null, null, null, expiresAt)
        );
        assertThat(refreshTokenStore.find(USER_ID, "missing")).isEmpty();
    }

    @Test
    @DisplayName("세션 하나 삭제 시 세션 목록에서도 제거")
    void deleteRemovesSessionAndMember() {
        // given
        saveSession("session-1");
        saveSession("session-2");

        // when
        refreshTokenStore.delete(USER_ID, "session-1");

        // then
        assertThat(redisTemplate.hasKey(SESSION_KEY)).isFalse();
        assertThat(redisTemplate.opsForSet().members(SESSIONS_KEY)).containsExactly("session-2");
    }

    @Test
    @DisplayName("사용자의 모든 세션 삭제")
    void deleteAllByUserIdRemovesAllSessions() {
        // given
        saveSession("session-1");
        saveSession("session-2");

        // when
        refreshTokenStore.deleteAllByUserId(USER_ID);

        // then
        assertThat(redisTemplate.keys("bookforest:auth:refresh:{7}:*")).isEmpty();
    }

    @Test
    @DisplayName("현재 토큰으로 교체하면 새 토큰 저장, 기존 토큰은 직전 토큰으로 보관")
    void rotateWithCurrentToken() {
        // given
        saveSession(SESSION_ID);
        Instant previousValidUntil = Instant.now().plusSeconds(30);
        Instant newExpiresAt = Instant.now().plus(2, ChronoUnit.HOURS);

        // when
        RefreshTokenRotationStatus status = refreshTokenStore.rotate(
            command("token-1", "hash-1", "token-2", "hash-2", previousValidUntil, newExpiresAt)
        );

        // then
        assertThat(status).isEqualTo(RefreshTokenRotationStatus.ROTATED);
        assertThat(refreshTokenStore.find(USER_ID, SESSION_ID)).hasValueSatisfying(session -> {
            assertThat(session.currentTokenId()).isEqualTo("token-2");
            assertThat(session.currentTokenHash()).isEqualTo("hash-2");
            assertThat(session.previousTokenId()).isEqualTo("token-1");
            assertThat(session.previousTokenHash()).isEqualTo("hash-1");
            assertThat(session.previousValidUntil()).isEqualTo(previousValidUntil.truncatedTo(ChronoUnit.MILLIS));
            assertThat(session.expiresAt()).isEqualTo(newExpiresAt.truncatedTo(ChronoUnit.MILLIS));
        });
    }

    @Test
    @DisplayName("유예 기간 안의 직전 토큰으로 교체 허용")
    void rotateWithPreviousTokenInGracePeriod() {
        // given
        saveSession(SESSION_ID);
        refreshTokenStore.rotate(command("token-1", "hash-1", "token-2", "hash-2", Instant.now().plusSeconds(30)));

        // when
        RefreshTokenRotationStatus status = refreshTokenStore.rotate(
            command("token-1", "hash-1", "token-3", "hash-3", Instant.now().plusSeconds(30))
        );

        // then
        assertThat(status).isEqualTo(RefreshTokenRotationStatus.ROTATED);
        assertThat(refreshTokenStore.find(USER_ID, SESSION_ID)).hasValueSatisfying(session -> {
            assertThat(session.currentTokenId()).isEqualTo("token-3");
            assertThat(session.previousTokenId()).isEqualTo("token-2");
        });
    }

    @Test
    @DisplayName("유예 기간이 지난 직전 토큰은 재사용으로 판단하고 세션 삭제")
    void rotateWithExpiredPreviousTokenDetectsReuse() {
        // given
        saveSession(SESSION_ID);
        refreshTokenStore.rotate(command("token-1", "hash-1", "token-2", "hash-2", Instant.now().minusSeconds(1)));

        // when
        RefreshTokenRotationStatus status = refreshTokenStore.rotate(
            command("token-1", "hash-1", "token-3", "hash-3", Instant.now().plusSeconds(30))
        );

        // then
        assertThat(status).isEqualTo(RefreshTokenRotationStatus.REUSE_DETECTED);
        assertThat(redisTemplate.hasKey(SESSION_KEY)).isFalse();
        assertThat(redisTemplate.opsForSet().isMember(SESSIONS_KEY, SESSION_ID)).isFalse();
    }

    @Test
    @DisplayName("다른 사용자의 세션 키로 교체하면 재사용으로 판단")
    void rotateWithDifferentUserDetectsReuse() {
        // given
        redisTemplate.opsForHash().putAll(SESSION_KEY, Map.of(
            "userId", "8",
            "currentTokenId", "token-1",
            "currentTokenHash", "hash-1",
            "expiresAt", "0"
        ));

        // when
        RefreshTokenRotationStatus status = refreshTokenStore.rotate(
            command("token-1", "hash-1", "token-2", "hash-2", Instant.now().plusSeconds(30))
        );

        // then
        assertThat(status).isEqualTo(RefreshTokenRotationStatus.REUSE_DETECTED);
        assertThat(redisTemplate.hasKey(SESSION_KEY)).isFalse();
    }

    @Test
    @DisplayName("없는 세션 교체 시 NOT_FOUND")
    void rotateMissingSession() {
        // when
        RefreshTokenRotationStatus status = refreshTokenStore.rotate(
            command("token-1", "hash-1", "token-2", "hash-2", Instant.now().plusSeconds(30))
        );

        // then
        assertThat(status).isEqualTo(RefreshTokenRotationStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("교체 요청 값이 비었거나 새 만료 시각이 지났으면 IllegalArgumentException")
    void rotateRejectsInvalidCommand() {
        Instant validUntil = Instant.now().plusSeconds(30);

        assertThatThrownBy(() -> refreshTokenStore.rotate(command(" ", "hash-1", "token-2", "hash-2", validUntil)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> refreshTokenStore.rotate(
            command("token-1", "hash-1", "token-2", "hash-2", validUntil, Instant.now().minusSeconds(1))
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> refreshTokenStore.rotate(null))
            .isInstanceOf(NullPointerException.class);
    }

    private void saveSession(String sessionId) {
        refreshTokenStore.save(RefreshTokenSession.initial(
            sessionId,
            USER_ID,
            "token-1",
            "hash-1",
            Instant.now().plus(Duration.ofHours(1))
        ));
    }

    private RefreshTokenRotationCommand command(
        String presentedTokenId,
        String presentedTokenHash,
        String newTokenId,
        String newTokenHash,
        Instant previousValidUntil
    ) {
        return command(
            presentedTokenId,
            presentedTokenHash,
            newTokenId,
            newTokenHash,
            previousValidUntil,
            Instant.now().plus(Duration.ofHours(2))
        );
    }

    private RefreshTokenRotationCommand command(
        String presentedTokenId,
        String presentedTokenHash,
        String newTokenId,
        String newTokenHash,
        Instant previousValidUntil,
        Instant newExpiresAt
    ) {
        return new RefreshTokenRotationCommand(
            USER_ID,
            SESSION_ID,
            presentedTokenId,
            presentedTokenHash,
            newTokenId,
            newTokenHash,
            previousValidUntil,
            newExpiresAt
        );
    }
}
