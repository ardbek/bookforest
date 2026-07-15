package com.ebook.bookforest.global.security.jwt.model;

import java.time.Instant;

public record ParsedRefreshToken(
    Long userId,
    String tokenId,
    String sessionId,
    Instant expiresAt
) {
}
