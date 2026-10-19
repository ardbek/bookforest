package com.ebook.bookforest.global.security.jwt.model;

import java.time.Instant;

/**
 * 검증을 통과한 Refresh Token 내용
 */
public record ParsedRefreshToken(
    Long userId,
    String tokenId,
    String sessionId,
    Instant expiresAt
) {
}
