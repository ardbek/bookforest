package com.ebook.bookforest.global.security.jwt.model;

import java.time.Instant;

/**
 * 발급한 Access Token
 *
 * @param value 클라이언트에 전달하는 JWT 문자열
 */
public record IssuedAccessToken(
    String value,
    String tokenId,
    Instant expiresAt
) {
}
