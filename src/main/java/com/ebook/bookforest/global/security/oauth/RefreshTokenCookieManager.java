package com.ebook.bookforest.global.security.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 쿠키 읽기, 쓰기, 만료
 * 재발급과 로그아웃 API에만 보내도록 경로를 /api로 제한하고, 스크립트에서 읽지 못하게 HttpOnly 적용
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieManager {

    private static final String COOKIE_PATH = "/api";

    private final OAuth2Properties properties;

    /** Refresh Token 쿠키 값 조회, 비어 있는 값은 없는 것으로 취급 */
    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
            .filter(cookie -> properties.refreshTokenCookieName().equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(value -> value != null && !value.isBlank())
            .findFirst();
    }

    /**
     * Refresh Token 쿠키 추가(토큰 만료 시각까지 유지)
     *
     * @throws IllegalArgumentException 만료 시각이 이미 지난 경우
     */
    public void add(HttpServletResponse response, String refreshToken, Instant expiresAt) {
        Duration maxAge = Duration.between(Instant.now(), expiresAt);
        if (maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException("Refresh Token 만료 시간이 올바르지 않습니다.");
        }

        writeCookie(response, refreshToken, maxAge);
    }

    public void expire(HttpServletResponse response) {
        writeCookie(response, "", Duration.ZERO);
    }

    private void writeCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(properties.refreshTokenCookieName(), value)
            .httpOnly(true)
            .secure(properties.cookieSecure())
            .sameSite(properties.cookieSameSite())
            .path(COOKIE_PATH)
            .maxAge(maxAge)
            .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
