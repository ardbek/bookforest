package com.ebook.bookforest.domain.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 플랫폼 토큰 암호화 설정
 *
 * @param encryptionKey Base64로 인코딩한 32바이트 AES 키
 */
@ConfigurationProperties(prefix = "app.security.social-token")
public record SocialTokenCryptoProperties(String encryptionKey) {

    public SocialTokenCryptoProperties {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalArgumentException("소셜 토큰 암호화 키 설정은 필수입니다.");
        }
    }
}
