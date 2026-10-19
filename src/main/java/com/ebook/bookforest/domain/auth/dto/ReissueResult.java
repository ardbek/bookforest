package com.ebook.bookforest.domain.auth.dto;

import java.time.Instant;

/**
 * 토큰 재발급 결과
 * Controller가 Refresh Token을 쿠키로, 나머지를 응답 본문으로 나누어 전달
 */
public record ReissueResult(
    String accessToken,
    /** Access Token 남은 유효 시간(초) */
    long accessTokenExpiresIn,
    String refreshToken,
    Instant refreshTokenExpiresAt,
    AuthUserResponse user
) {
}
