package com.ebook.bookforest.global.security.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.oauth2")
public record OAuth2Properties(
    String frontCallbackUrl,
    String refreshTokenCookieName,
    boolean cookieSecure,
    String cookieSameSite
) {

    public OAuth2Properties{
        if(frontCallbackUrl == null || frontCallbackUrl.isBlank()) {
            throw new IllegalArgumentException("프론트 OAuth 콜백 URL 설정은 필수입니다.");
        }

        if(refreshTokenCookieName == null || refreshTokenCookieName.isBlank()) {
            throw new IllegalArgumentException("Refresh Token 쿠키 이름 설정은 필수입니다.");
        }

        if(cookieSameSite == null || cookieSameSite.isBlank()) {
            throw new IllegalArgumentException("쿠키 SameSite 설정은 필수입니다.");
        }

        if ("None".equalsIgnoreCase(cookieSameSite) && !cookieSecure) {
            throw new IllegalArgumentException("SameSite=None 쿠키는 Secure=true가 필요합니다.");
        }

    }

}
