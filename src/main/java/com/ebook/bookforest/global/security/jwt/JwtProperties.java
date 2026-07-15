package com.ebook.bookforest.global.security.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
    String secret,
    Duration accessTokenTtl,
    Duration refreshTokenTtl
) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException(
                "JWT secret은 반드시 설정해야 합니다."
            );
        }

        if (accessTokenTtl == null
            || accessTokenTtl.isZero()
            || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                "Access Token 유효시간이 올바르지 않습니다."
            );
        }

        if (refreshTokenTtl == null
            || refreshTokenTtl.isZero()
            || refreshTokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                "Refresh Token 유효시간이 올바르지 않습니다."
            );
        }
    }
}
