package com.ebook.bookforest.domain.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 연동 해제 API 인증 설정, 값이 없으면 애플리케이션 시작 실패
 */
@ConfigurationProperties(prefix = "app.security.social-unlink")
public record SocialUnlinkProperties(
    String kakaoAdminKey,
    String naverClientId,
    String naverClientSecret
) {

    public SocialUnlinkProperties {
        requireValue(kakaoAdminKey, "카카오 Admin Key");
        requireValue(naverClientId, "네이버 Client ID");
        requireValue(naverClientSecret, "네이버 Client Secret");
    }

    private static void requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 설정은 필수입니다.");
        }
    }
}
