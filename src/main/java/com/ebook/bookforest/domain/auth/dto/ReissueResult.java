package com.ebook.bookforest.domain.auth.dto;

import java.time.Instant;

public record ReissueResult(
    String accessToken,
    long accessTokenExpiresIn,
    String refreshToken,
    Instant refreshTokenExpiresAt,
    AuthUserResponse user
) {
}
