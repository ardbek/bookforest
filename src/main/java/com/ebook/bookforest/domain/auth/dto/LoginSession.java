package com.ebook.bookforest.domain.auth.dto;

import java.time.Instant;

public record LoginSession(
    String refreshToken,
    Instant refreshTokenExpiresAt
) {
}
