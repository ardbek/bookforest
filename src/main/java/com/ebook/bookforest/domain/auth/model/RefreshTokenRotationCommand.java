package com.ebook.bookforest.domain.auth.model;

import java.time.Instant;

public record RefreshTokenRotationCommand(
    Long userId,
    String sessionId,
    String presentedTokenId,
    String presentedTokenHash,
    String newTokenId,
    String newTokenHash,
    Instant previousValidUntil,
    Instant newExpiresAt
) {
}