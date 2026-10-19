package com.ebook.bookforest.global.security.jwt.model;

import java.time.Instant;

/**
 * 발급한 Refresh Token
 *
 * @param value 쿠키로 전달하는 JWT 문자열, 저장소에는 해시만 저장
 */
public record IssuedRefreshToken(
    String value,
    String tokenId,
    String sessionId,
    Instant expiresAt
) {
}
