package com.ebook.bookforest.global.security.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RefreshTokenCookieManagerTest {

    private static final String COOKIE_NAME = "refresh_token";

    private RefreshTokenCookieManager cookieManager;

    @BeforeEach
    void setUp() {
        OAuth2Properties properties = new OAuth2Properties(
            "http://localhost:3000/auth/callback",
            COOKIE_NAME,
            false,
            "Lax"
        );
        cookieManager = new RefreshTokenCookieManager(properties);
    }

    @Test
    void 요청_쿠키에서_설정된_이름의_Refresh_Token을_읽는다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
            new Cookie("other", "other-value"),
            new Cookie(COOKIE_NAME, "raw-refresh-token")
        );

        assertEquals(
            "raw-refresh-token",
            cookieManager.read(request).orElseThrow()
        );
    }

    @Test
    void 쿠키가_없거나_Refresh_Token이_비어_있으면_empty를_반환한다() {
        MockHttpServletRequest requestWithoutCookies =
            new MockHttpServletRequest();

        assertTrue(cookieManager.read(requestWithoutCookies).isEmpty());

        MockHttpServletRequest requestWithBlankCookie =
            new MockHttpServletRequest();
        requestWithBlankCookie.setCookies(
            new Cookie(COOKIE_NAME, "   ")
        );

        assertTrue(cookieManager.read(requestWithBlankCookie).isEmpty());
    }

    @Test
    void Refresh_Token을_HttpOnly_쿠키로_추가한다() {
        MockHttpServletResponse response =
            new MockHttpServletResponse();

        cookieManager.add(
            response,
            "raw-refresh-token",
            Instant.now().plusSeconds(3600)
        );

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie.startsWith(
            COOKIE_NAME + "=raw-refresh-token"
        ));
        assertTrue(setCookie.contains("Path=/api"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        assertTrue(setCookie.contains("Max-Age="));
        assertFalse(setCookie.contains("Max-Age=0"));
        assertFalse(setCookie.contains("Secure"));
    }

    @Test
    void Refresh_Token_쿠키를_동일한_경로와_MaxAge_0으로_만료시킨다() {
        MockHttpServletResponse response =
            new MockHttpServletResponse();

        cookieManager.expire(response);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(setCookie.startsWith(COOKIE_NAME + "="));
        assertTrue(setCookie.contains("Path=/api"));
        assertTrue(setCookie.contains("Max-Age=0"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
    }

    @Test
    void 이미_만료된_Refresh_Token은_쿠키로_추가할_수_없다() {
        assertThrows(
            IllegalArgumentException.class,
            () -> cookieManager.add(
                new MockHttpServletResponse(),
                "raw-refresh-token",
                Instant.now().minusSeconds(1)
            )
        );
    }
}
