package com.ebook.bookforest.domain.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ebook.bookforest.domain.auth.dto.AuthUserResponse;
import com.ebook.bookforest.domain.auth.dto.ReissueResponse;
import com.ebook.bookforest.domain.auth.dto.ReissueResult;
import com.ebook.bookforest.domain.auth.exception.AuthErrorCode;
import com.ebook.bookforest.domain.auth.exception.AuthException;
import com.ebook.bookforest.domain.auth.service.AuthService;
import com.ebook.bookforest.global.security.oauth.OAuth2Properties;
import com.ebook.bookforest.global.security.oauth.RefreshTokenCookieManager;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthControllerTest {

    private static final String FRONT_ORIGIN =
        "http://localhost:3000";
    private static final String RAW_REFRESH_TOKEN =
        "raw-refresh-token";
    private static final String NEW_REFRESH_TOKEN =
        "new-refresh-token";
    private static final Instant NEW_REFRESH_EXPIRES_AT =
        Instant.parse("2099-01-01T00:00:00Z");

    private AuthService authService;
    private RefreshTokenCookieManager cookieManager;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        cookieManager = mock(RefreshTokenCookieManager.class);

        OAuth2Properties properties = new OAuth2Properties(
            FRONT_ORIGIN + "/auth/callback",
            "refresh_token",
            false,
            "Lax"
        );

        controller = new AuthController(
            authService,
            properties,
            cookieManager
        );
    }

    @Test
    void reissue에_성공하면_Access_Token_응답과_새_Refresh_Token_쿠키를_반환한다() {
        MockHttpServletRequest request = validOriginRequest();
        MockHttpServletResponse response =
            new MockHttpServletResponse();
        when(cookieManager.read(request))
            .thenReturn(Optional.of(RAW_REFRESH_TOKEN));

        AuthUserResponse user = new AuthUserResponse(
            1L,
            "책벌레",
            "https://example.com/profile.png"
        );
        ReissueResult serviceResult = new ReissueResult(
            "new-access-token",
            1800L,
            NEW_REFRESH_TOKEN,
            NEW_REFRESH_EXPIRES_AT,
            user
        );
        when(authService.reissue(RAW_REFRESH_TOKEN))
            .thenReturn(serviceResult);

        ResponseEntity<ReissueResponse> result =
            controller.reissue(request, response);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(
            new ReissueResponse(
                "new-access-token",
                1800L,
                user
            ),
            result.getBody()
        );
        verify(cookieManager).add(
            response,
            NEW_REFRESH_TOKEN,
            NEW_REFRESH_EXPIRES_AT
        );
    }

    @Test
    void Refresh_Token_쿠키가_없으면_MISSING_REFRESH_TOKEN_오류가_발생한다() {
        MockHttpServletRequest request = validOriginRequest();
        when(cookieManager.read(request))
            .thenReturn(Optional.empty());

        AuthException exception = assertThrows(
            AuthException.class,
            () -> controller.reissue(
                request,
                new MockHttpServletResponse()
            )
        );

        assertEquals(
            AuthErrorCode.MISSING_REFRESH_TOKEN,
            exception.getErrorCode()
        );
        verifyNoInteractions(authService);
    }

    @Test
    void reissue가_실패하면_Refresh_Token_쿠키를_만료시킨다() {
        MockHttpServletRequest request = validOriginRequest();
        MockHttpServletResponse response =
            new MockHttpServletResponse();
        when(cookieManager.read(request))
            .thenReturn(Optional.of(RAW_REFRESH_TOKEN));
        when(authService.reissue(RAW_REFRESH_TOKEN))
            .thenThrow(new AuthException(
                AuthErrorCode.INVALID_REFRESH_TOKEN
            ));

        AuthException exception = assertThrows(
            AuthException.class,
            () -> controller.reissue(request, response)
        );

        assertEquals(
            AuthErrorCode.INVALID_REFRESH_TOKEN,
            exception.getErrorCode()
        );
        verify(cookieManager).expire(response);
    }

    @Test
    void 허용되지_않은_Origin의_reissue는_서비스를_호출하지_않는다() {
        MockHttpServletRequest request =
            new MockHttpServletRequest();
        request.addHeader(
            HttpHeaders.ORIGIN,
            "https://evil.example"
        );

        AuthException exception = assertThrows(
            AuthException.class,
            () -> controller.reissue(
                request,
                new MockHttpServletResponse()
            )
        );

        assertEquals(
            AuthErrorCode.INVALID_ORIGIN,
            exception.getErrorCode()
        );
        verifyNoInteractions(authService, cookieManager);
    }

    @Test
    void logout은_Redis_세션을_삭제하고_쿠키를_만료시킨다() {
        MockHttpServletRequest request = validOriginRequest();
        MockHttpServletResponse response =
            new MockHttpServletResponse();
        when(cookieManager.read(request))
            .thenReturn(Optional.of(RAW_REFRESH_TOKEN));

        ResponseEntity<Void> result =
            controller.logout(request, response);

        assertEquals(
            HttpStatus.NO_CONTENT,
            result.getStatusCode()
        );
        verify(authService).logout(RAW_REFRESH_TOKEN);
        verify(cookieManager).expire(response);
    }

    @Test
    void Refresh_Token이_없는_logout도_성공하고_쿠키를_만료시킨다() {
        MockHttpServletRequest request = validOriginRequest();
        MockHttpServletResponse response =
            new MockHttpServletResponse();
        when(cookieManager.read(request))
            .thenReturn(Optional.empty());

        ResponseEntity<Void> result =
            controller.logout(request, response);

        assertEquals(
            HttpStatus.NO_CONTENT,
            result.getStatusCode()
        );
        verify(authService, never()).logout(anyString());
        verify(cookieManager).expire(response);
    }

    private MockHttpServletRequest validOriginRequest() {
        MockHttpServletRequest request =
            new MockHttpServletRequest();
        request.addHeader(
            HttpHeaders.ORIGIN,
            FRONT_ORIGIN
        );
        return request;
    }
}
