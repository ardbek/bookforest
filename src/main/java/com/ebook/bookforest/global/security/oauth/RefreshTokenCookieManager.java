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

@Component
@RequiredArgsConstructor
public class RefreshTokenCookieManager {

    private static final String COOKIE_PATH = "/api";
    private final OAuth2Properties properties;

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
            .filter(cookie ->
                properties.refreshTokenCookieName().equals(cookie.getName())
            )
            .map(Cookie::getValue)
            .filter(value ->
                value != null && !value.isBlank()
            ).findFirst();
    }

    public void add( HttpServletResponse response, String refreshToken, Instant expiresAt) {
        Duration maxAge = Duration.between(Instant.now(), expiresAt);

        if (maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException("Refresh Token 만료 시간이 올바르지 않습니다.");
        }

        writeCookie( response, refreshToken, maxAge);
    }

    public void expire(HttpServletResponse response) {
        writeCookie(response, "", Duration.ZERO);
    }

    private void writeCookie( HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie
            .from(
                properties.refreshTokenCookieName(),
                value
            )
            .httpOnly(true)
            .secure(properties.cookieSecure())
            .sameSite(properties.cookieSameSite())
            .path(COOKIE_PATH)
            .maxAge(maxAge)
            .build();

        response.addHeader(
            HttpHeaders.SET_COOKIE,
            cookie.toString()
        );
    }

}
