package com.ebook.bookforest.domain.auth.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebook.bookforest.domain.auth.dto.AuthUserResponse;
import com.ebook.bookforest.domain.auth.dto.ReissueResult;
import com.ebook.bookforest.domain.auth.exception.AuthErrorCode;
import com.ebook.bookforest.domain.auth.exception.AuthException;
import com.ebook.bookforest.domain.auth.service.AuthService;
import com.ebook.bookforest.domain.searchkeyword.config.SearchKeywordWebConfig;
import com.ebook.bookforest.global.security.jwt.JwtAuthenticationFilter;
import com.ebook.bookforest.global.security.jwt.JwtExceptionHandlerFilter;
import com.ebook.bookforest.support.RefreshTokenCookieTestConfig;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 토큰 재발급, 로그아웃 API 계약 검증
 * 실제 쿠키 관리자를 써서 응답 JSON과 Refresh Token 쿠키 헤더를 함께 확인
 */
@WebMvcTest(
    controllers = AuthController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            JwtAuthenticationFilter.class,
            JwtExceptionHandlerFilter.class,
            SearchKeywordWebConfig.class
        }
    )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(RefreshTokenCookieTestConfig.class)
class AuthControllerTest {

    private static final String FRONT_ORIGIN = "http://localhost:3000";
    private static final String RAW_REFRESH_TOKEN = "raw-refresh-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";
    /** 운영 Refresh Token 유효 기간과 같은 14일, 쿠키 Max-Age가 int 범위를 넘지 않는 값 */
    private static final Instant NEW_REFRESH_EXPIRES_AT = Instant.now().plus(Duration.ofDays(14));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("재발급에 성공하면 Access Token 응답 JSON과 새 Refresh Token 쿠키 반환")
    void reissueReturnsAccessTokenAndNewRefreshTokenCookie() throws Exception {
        // given
        given(authService.reissue(RAW_REFRESH_TOKEN)).willReturn(new ReissueResult(
            "new-access-token",
            1800L,
            NEW_REFRESH_TOKEN,
            NEW_REFRESH_EXPIRES_AT,
            new AuthUserResponse(1L, "책벌레", "https://example.com/profile.png")
        ));

        // when, then
        mockMvc.perform(post("/api/reissue")
                .header(HttpHeaders.ORIGIN, FRONT_ORIGIN)
                .cookie(refreshTokenCookie()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", aMapWithSize(3)))
            .andExpect(jsonPath("$.accessToken").value("new-access-token"))
            .andExpect(jsonPath("$.expiresIn").value(1800))
            .andExpect(jsonPath("$.user", aMapWithSize(3)))
            .andExpect(jsonPath("$.user.userId").value(1))
            .andExpect(jsonPath("$.user.displayName").value("책벌레"))
            .andExpect(jsonPath("$.user.profileImageUrl").value("https://example.com/profile.png"))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                containsString("refresh_token=" + NEW_REFRESH_TOKEN),
                containsString("Path=/api"),
                containsString("HttpOnly"),
                containsString("SameSite=Lax")
            )));
    }

    @Test
    @DisplayName("Refresh Token 쿠키가 없으면 missing_refresh_token 오류, 재발급 미호출")
    void reissueRejectsMissingCookie() throws Exception {
        // when, then
        mockMvc.perform(post("/api/reissue").header(HttpHeaders.ORIGIN, FRONT_ORIGIN))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("missing_refresh_token"));

        then(authService).should(never()).reissue(any());
    }

    @Test
    @DisplayName("재발급이 인증 오류로 실패하면 Refresh Token 쿠키를 만료시키고 오류 응답")
    void reissueExpiresCookieWhenServiceFails() throws Exception {
        // given
        given(authService.reissue(RAW_REFRESH_TOKEN))
            .willThrow(new AuthException(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED));

        // when, then
        mockMvc.perform(post("/api/reissue")
                .header(HttpHeaders.ORIGIN, FRONT_ORIGIN)
                .cookie(refreshTokenCookie()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("refresh_token_reuse_detected"))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                containsString("refresh_token=;"),
                containsString("Max-Age=0")
            )));
    }

    @Test
    @DisplayName("허용되지 않은 Origin의 재발급 요청은 invalid_origin 오류, 재발급 미호출")
    void reissueRejectsInvalidOrigin() throws Exception {
        // when, then
        mockMvc.perform(post("/api/reissue")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .cookie(refreshTokenCookie()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("invalid_origin"));

        then(authService).should(never()).reissue(any());
    }

    @Test
    @DisplayName("로그아웃은 Refresh Token 세션을 삭제하고 쿠키를 만료시킨 뒤 본문 없이 204 응답")
    void logoutDeletesSessionAndExpiresCookie() throws Exception {
        // when, then
        mockMvc.perform(post("/api/logout")
                .header(HttpHeaders.ORIGIN, FRONT_ORIGIN)
                .cookie(refreshTokenCookie()))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        then(authService).should().logout(RAW_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("Refresh Token 쿠키 없는 로그아웃도 쿠키를 만료시키고 204 응답, 세션 삭제 미호출")
    void logoutWithoutCookieStillSucceeds() throws Exception {
        // when, then
        mockMvc.perform(post("/api/logout").header(HttpHeaders.ORIGIN, FRONT_ORIGIN))
            .andExpect(status().isNoContent())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        then(authService).should(never()).logout(any());
    }

    private Cookie refreshTokenCookie() {
        return new Cookie("refresh_token", RAW_REFRESH_TOKEN);
    }
}
