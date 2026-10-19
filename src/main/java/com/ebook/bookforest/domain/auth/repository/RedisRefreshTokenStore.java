package com.ebook.bookforest.domain.auth.repository;

import com.ebook.bookforest.domain.auth.model.RefreshTokenRotationCommand;
import com.ebook.bookforest.domain.auth.model.RefreshTokenRotationStatus;
import com.ebook.bookforest.domain.auth.model.RefreshTokenSession;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * Redis 기반 Refresh Token 세션 저장소
 * 세션은 Hash, 사용자별 세션 ID 목록은 Set으로 저장
 * 키 형식과 Hash 필드 이름은 운영 Redis에 저장된 세션과 Lua 스크립트가 함께 쓰므로 바꾸지 않음
 */
@Repository
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "bookforest:auth:refresh";
    private static final RedisScript<Long> ROTATION_SCRIPT =
        RedisScript.of(new ClassPathResource("redis/rotate_refresh_token.lua"), Long.class);

    private static final String USER_ID_FIELD = "userId";
    private static final String CURRENT_TOKEN_ID_FIELD = "currentTokenId";
    private static final String CURRENT_TOKEN_HASH_FIELD = "currentTokenHash";
    private static final String PREVIOUS_TOKEN_ID_FIELD = "previousTokenId";
    private static final String PREVIOUS_TOKEN_HASH_FIELD = "previousTokenHash";
    private static final String PREVIOUS_VALID_UNTIL_FIELD = "previousValidUntil";
    private static final String EXPIRES_AT_FIELD = "expiresAt";

    private final StringRedisTemplate redisTemplate;

    /** 세션 저장, 이미 만료된 세션은 저장하지 않음 */
    @Override
    public void save(RefreshTokenSession session) {
        Duration ttl = Duration.between(Instant.now(), session.expiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }

        String sessionKey = sessionKey(session.userId(), session.sessionId());
        String sessionsKey = sessionsKey(session.userId());

        Map<String, String> values = new HashMap<>();
        values.put(USER_ID_FIELD, String.valueOf(session.userId()));
        values.put(CURRENT_TOKEN_ID_FIELD, session.currentTokenId());
        values.put(CURRENT_TOKEN_HASH_FIELD, session.currentTokenHash());
        values.put(EXPIRES_AT_FIELD, String.valueOf(session.expiresAt().toEpochMilli()));

        HashOperations<String, String, String> hashOperations = redisTemplate.opsForHash();
        hashOperations.putAll(sessionKey, values);
        redisTemplate.expire(sessionKey, ttl);

        redisTemplate.opsForSet().add(sessionsKey, session.sessionId());
        redisTemplate.expire(sessionsKey, ttl);
    }

    @Override
    public Optional<RefreshTokenSession> find(Long userId, String sessionId) {
        HashOperations<String, String, String> hashOperations = redisTemplate.opsForHash();
        Map<String, String> values = hashOperations.entries(sessionKey(userId, sessionId));
        if (values.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new RefreshTokenSession(
            sessionId,
            Long.valueOf(values.get(USER_ID_FIELD)),
            values.get(CURRENT_TOKEN_ID_FIELD),
            values.get(CURRENT_TOKEN_HASH_FIELD),
            values.get(PREVIOUS_TOKEN_ID_FIELD),
            values.get(PREVIOUS_TOKEN_HASH_FIELD),
            toInstant(values.get(PREVIOUS_VALID_UNTIL_FIELD)),
            Instant.ofEpochMilli(Long.parseLong(values.get(EXPIRES_AT_FIELD)))
        ));
    }

    /**
     * 토큰 교체(Lua 스크립트로 원자적 처리)
     *
     * <p>스크립트 반환값별 결과
     * <ul>
     *   <li>0: NOT_FOUND</li>
     *   <li>1: ROTATED</li>
     *   <li>2: REUSE_DETECTED, 스크립트가 해당 세션 삭제</li>
     * </ul>
     *
     * @throws IllegalArgumentException 요청 값이 비었거나 새 만료 시각이 이미 지난 경우
     */
    @Override
    public RefreshTokenRotationStatus rotate(RefreshTokenRotationCommand command) {
        validateRotationCommand(command);

        Instant now = Instant.now();
        Duration ttl = Duration.between(now, command.newExpiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("새 Refresh Token의 만료 시각이 올바르지 않습니다.");
        }

        Long result = redisTemplate.execute(
            ROTATION_SCRIPT,
            List.of(sessionKey(command.userId(), command.sessionId()), sessionsKey(command.userId())),
            command.presentedTokenId(),
            command.presentedTokenHash(),
            command.newTokenId(),
            command.newTokenHash(),
            String.valueOf(now.toEpochMilli()),
            String.valueOf(command.previousValidUntil().toEpochMilli()),
            String.valueOf(command.newExpiresAt().toEpochMilli()),
            String.valueOf(ttl.toMillis()),
            command.sessionId(),
            String.valueOf(command.userId())
        );
        if (result == null) {
            throw new IllegalStateException("Redis RT rotation 결과를 받지 못했습니다.");
        }

        return switch (result.intValue()) {
            case 0 -> RefreshTokenRotationStatus.NOT_FOUND;
            case 1 -> RefreshTokenRotationStatus.ROTATED;
            case 2 -> RefreshTokenRotationStatus.REUSE_DETECTED;
            default -> throw new IllegalStateException("알 수 없는 Redis RT rotation 결과입니다: " + result);
        };
    }

    @Override
    public void delete(Long userId, String sessionId) {
        redisTemplate.delete(sessionKey(userId, sessionId));
        redisTemplate.opsForSet().remove(sessionsKey(userId), sessionId);
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        String sessionsKey = sessionsKey(userId);

        Set<String> sessionIds = redisTemplate.opsForSet().members(sessionsKey);
        if (sessionIds != null && !sessionIds.isEmpty()) {
            List<String> sessionKeys = sessionIds.stream()
                .map(sessionId -> sessionKey(userId, sessionId))
                .toList();
            redisTemplate.delete(sessionKeys);
        }

        redisTemplate.delete(sessionsKey);
    }

    // Lua 스크립트가 세션 키와 목록 키를 함께 다루므로 Redis Cluster에서도 같은 슬롯에 놓이도록 사용자 ID를 해시 태그로 감쌈
    private String sessionKey(Long userId, String sessionId) {
        return KEY_PREFIX + ":{" + userId + "}:session:" + sessionId;
    }

    private String sessionsKey(Long userId) {
        return KEY_PREFIX + ":{" + userId + "}:sessions";
    }

    private Instant toInstant(String epochMillis) {
        if (epochMillis == null) {
            return null;
        }
        return Instant.ofEpochMilli(Long.parseLong(epochMillis));
    }

    private void validateRotationCommand(RefreshTokenRotationCommand command) {
        Objects.requireNonNull(command, "command는 null일 수 없습니다.");
        Objects.requireNonNull(command.userId(), "userId는 null일 수 없습니다.");
        Objects.requireNonNull(command.newExpiresAt(), "newExpiresAt은 null일 수 없습니다.");
        Objects.requireNonNull(command.previousValidUntil(), "previousValidUntil은 null일 수 없습니다.");

        requireText(command.sessionId(), "sessionId");
        requireText(command.presentedTokenId(), "presentedTokenId");
        requireText(command.presentedTokenHash(), "presentedTokenHash");
        requireText(command.newTokenId(), "newTokenId");
        requireText(command.newTokenHash(), "newTokenHash");
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다.");
        }
    }
}
