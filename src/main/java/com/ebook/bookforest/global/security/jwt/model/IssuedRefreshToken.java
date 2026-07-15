package com.ebook.bookforest.global.security.jwt.model;

import java.time.Instant;

public record IssuedRefreshToken(
    String value,
    String tokenId,
    String sessionId,
    Instant expiresAt
) {
}