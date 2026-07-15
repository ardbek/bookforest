package com.ebook.bookforest.domain.auth.model;

import java.time.Instant;

public record RefreshTokenSession(
    String sessionId,
    Long userId,
    String currentTokenId,
    String currentTokenHash,
    String previousTokenId,
    String previousTokenHash,
    Instant previousValidUntil,
    Instant expiresAt
) {

    public static RefreshTokenSession initial(
        String sessionId,
        Long userId,
        String tokenId,
        String tokenHash,
        Instant expiresAt
    ) {
        return new RefreshTokenSession(
            sessionId,
            userId,
            tokenId,
            tokenHash,
            null,
            null,
            null,
            expiresAt
        );
    }
}
