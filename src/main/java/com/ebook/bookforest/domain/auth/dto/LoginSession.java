package com.ebook.bookforest.domain.auth.dto;

import java.time.Instant;

/**
 * 소셜 로그인 성공 직후 발급한 Refresh Token과 만료 시각
 */
public record LoginSession(
    String refreshToken,
    Instant refreshTokenExpiresAt
) {
}
