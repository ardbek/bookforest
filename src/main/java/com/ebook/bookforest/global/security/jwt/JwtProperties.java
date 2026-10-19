package com.ebook.bookforest.global.security.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 서명 키와 토큰 유효 시간 설정, 값이 없거나 0 이하이면 애플리케이션 시작 실패
 *
 * @param secret Base64로 인코딩한 HMAC 서명 키
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
    String secret,
    Duration accessTokenTtl,
    Duration refreshTokenTtl
) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret은 반드시 설정해야 합니다.");
        }
        if (!isPositive(accessTokenTtl)) {
            throw new IllegalArgumentException("Access Token 유효시간이 올바르지 않습니다.");
        }
        if (!isPositive(refreshTokenTtl)) {
            throw new IllegalArgumentException("Refresh Token 유효시간이 올바르지 않습니다.");
        }
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
