package com.ebook.bookforest.global.security.oauth.unlink;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
