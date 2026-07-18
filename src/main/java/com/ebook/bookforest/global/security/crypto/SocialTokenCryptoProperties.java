package com.ebook.bookforest.global.security.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 암호화 설정(소셜 로그인 플랫폼 Access/Refresh token)
 */
@ConfigurationProperties(prefix = "app.security.social-token")
public record SocialTokenCryptoProperties(String encryptionKey) {

    public SocialTokenCryptoProperties {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalArgumentException("소셜 토큰 암호화 키 설정은 필수입니다.");
        }
    }
}
