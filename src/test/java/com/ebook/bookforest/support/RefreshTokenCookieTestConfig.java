package com.ebook.bookforest.support;

import com.ebook.bookforest.global.security.oauth.OAuth2Properties;
import com.ebook.bookforest.global.security.oauth.RefreshTokenCookieManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Controller 테스트용 Refresh Token 쿠키 설정
 * 로컬 .env 값과 무관하게 같은 쿠키 속성으로 검증하기 위한 고정 설정
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(RefreshTokenCookieManager.class)
public class RefreshTokenCookieTestConfig {

    public static final String FRONT_ORIGIN = "http://localhost:3000";
    public static final String COOKIE_NAME = "refresh_token";

    @Bean
    OAuth2Properties oAuth2Properties() {
        return new OAuth2Properties(FRONT_ORIGIN + "/auth/callback", COOKIE_NAME, false, "Lax");
    }
}
