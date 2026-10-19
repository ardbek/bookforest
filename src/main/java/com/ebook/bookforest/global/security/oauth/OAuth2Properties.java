package com.ebook.bookforest.global.security.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 로그인 후 이동할 프론트 주소와 Refresh Token 쿠키 설정, 값이 올바르지 않으면 애플리케이션 시작 실패
 *
 * @param cookieSameSite SameSite 속성 값, None이면 브라우저 규칙상 cookieSecure가 true여야 함
 */
@ConfigurationProperties(prefix = "app.security.oauth2")
public record OAuth2Properties(
    String frontCallbackUrl,
    String refreshTokenCookieName,
    boolean cookieSecure,
    String cookieSameSite
) {

    public OAuth2Properties {
        if (frontCallbackUrl == null || frontCallbackUrl.isBlank()) {
            throw new IllegalArgumentException("프론트 OAuth 콜백 URL 설정은 필수입니다.");
        }
        if (refreshTokenCookieName == null || refreshTokenCookieName.isBlank()) {
            throw new IllegalArgumentException("Refresh Token 쿠키 이름 설정은 필수입니다.");
        }
        if (cookieSameSite == null || cookieSameSite.isBlank()) {
            throw new IllegalArgumentException("쿠키 SameSite 설정은 필수입니다.");
        }
        if ("None".equalsIgnoreCase(cookieSameSite) && !cookieSecure) {
            throw new IllegalArgumentException("SameSite=None 쿠키는 Secure=true가 필요합니다.");
        }
    }
}
