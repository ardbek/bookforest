package com.ebook.bookforest.global.redis;

import com.ebook.bookforest.domain.auth.model.RefreshTokenRotationCommand;
import com.ebook.bookforest.domain.auth.model.RefreshTokenRotationStatus;
import com.ebook.bookforest.domain.auth.model.RefreshTokenSession;
import com.ebook.bookforest.domain.auth.repository.RefreshTokenStore;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String PREFIX = "bookforest:auth:refresh";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> refreshTokenRotationScript;

    @Override
    public void save(RefreshTokenSession session) {
        String sessionKey = sessionKey(session.userId(), session.sessionId());
        String sessionsKey = sessionsKey(session.userId());

        Duration ttl = Duration.between(Instant.now(), session.expiresAt());

        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }

        Map<String, String> values = new HashMap<>();
        values.put("userId", String.valueOf(session.userId()));
        values.put("currentTokenId", session.currentTokenId());
        values.put("currentTokenHash", session.currentTokenHash());
        values.put("expiresAt", String.valueOf(session.expiresAt().toEpochMilli()));

        HashOperations<String, String, String> hashOperations = redisTemplate.opsForHash();

        hashOperations.putAll(sessionKey, values);

        redisTemplate.expire(sessionKey, ttl);
        redisTemplate.opsForSet().add( sessionsKey, session.sessionId());
        redisTemplate.expire(sessionsKey, ttl);
    }

    @Override
    public Optional<RefreshTokenSession> find(
        Long userId,
        String sessionId
    ) {
        String key = sessionKey(userId, sessionId);

        HashOperations<String, String, String> hashOperations = redisTemplate.opsForHash();

        Map<String, String> values = hashOperations.entries(key);

        if (values.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new RefreshTokenSession(
            sessionId,
            Long.valueOf(values.get("userId")),
            values.get("currentTokenId"),
            values.get("currentTokenHash"),
            values.get("previousTokenId"),
            values.get("previousTokenHash"),
            toInstant(values.get("previousValidUntil")),
            Instant.ofEpochMilli(Long.parseLong(values.get("expiresAt")))
        ));
    }

    @Override
    public void delete(Long userId, String sessionId) {
        redisTemplate.delete(sessionKey(userId, sessionId));

        redisTemplate.opsForSet().remove(
            sessionsKey(userId),
            sessionId
        );
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

    private String sessionKey(Long userId, String sessionId) {
        return PREFIX + ":{" + userId + "}:session:" + sessionId;
    }

    private String sessionsKey(Long userId) {
        return PREFIX + ":{" + userId + "}:sessions";
    }

    private Instant toInstant(String value) {
        if (value == null) {
            return null;
        }

        return Instant.ofEpochMilli(Long.parseLong(value));
    }

    @Override
    public RefreshTokenRotationStatus rotate(RefreshTokenRotationCommand command) {
        validateRotationCommand(command);

        Instant now = Instant.now();

        Duration ttl = Duration.between(
            now,
            command.newExpiresAt()
        );

        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("새 Refresh Token의 만료 시각이 올바르지 않습니다.");
        }

        String sessionKey = sessionKey(command.userId(), command.sessionId());

        String sessionsKey = sessionsKey(command.userId());

        Long result = redisTemplate.execute(
            refreshTokenRotationScript,
            List.of(sessionKey, sessionsKey),
            command.presentedTokenId(),
            command.presentedTokenHash(),
            command.newTokenId(),
            command.newTokenHash(),
            String.valueOf(now.toEpochMilli()),
            String.valueOf(
                command.previousValidUntil().toEpochMilli()
            ),
            String.valueOf(
                command.newExpiresAt().toEpochMilli()
            ),
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

