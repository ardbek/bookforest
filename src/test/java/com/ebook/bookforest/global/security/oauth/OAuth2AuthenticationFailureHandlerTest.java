package com.ebook.bookforest.global.security.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class OAuth2AuthenticationFailureHandlerTest {

    private static final String CALLBACK_URL = "http://localhost:3000/auth/callback";

    private OAuth2AuthenticationFailureHandler failureHandler;

    @BeforeEach
    void setUp() {
        OAuth2Properties properties = new OAuth2Properties(
            CALLBACK_URL,
            "refresh_token",
            false,
            "Lax"
        );

        failureHandler = new OAuth2AuthenticationFailureHandler(
            properties
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "access_denied",
        "inactive_user",
        "social_unlink_in_progress",
        "unsupported_provider",
        "invalid_oauth_response",
        "authorization_request_not_found",
        "already_linked"
    })
    void 허용된_OAuth2_오류_코드는_프론트_콜백에_그대로_전달한다(
        String errorCode
    ) throws Exception {
        OAuth2AuthenticationException exception =
            new OAuth2AuthenticationException(
                new OAuth2Error(errorCode),
                "provider error"
            );

        MockHttpServletResponse response = execute(exception);

        assertEquals(
            CALLBACK_URL + "?error=" + errorCode,
            response.getRedirectedUrl()
        );
        assertEquals(302, response.getStatus());
    }

    @Test
    void 허용되지_않은_OAuth2_오류는_공통_오류로_변환한다()
        throws Exception {

        OAuth2AuthenticationException exception =
            new OAuth2AuthenticationException(
                new OAuth2Error("provider_internal_error"),
                "노출되면 안 되는 제공자 오류 메시지"
            );

        MockHttpServletResponse response = execute(exception);

        assertEquals(
            CALLBACK_URL + "?error=oauth2_error",
            response.getRedirectedUrl()
        );
        assertFalse(
            response.getRedirectedUrl().contains("provider_internal_error")
        );
        assertFalse(
            response.getRedirectedUrl().contains("노출되면")
        );
    }

    @Test
    void 일반_인증_예외도_공통_OAuth2_오류로_변환한다()
        throws Exception {

        MockHttpServletResponse response = execute(
            new BadCredentialsException("internal message")
        );

        assertEquals(
            CALLBACK_URL + "?error=oauth2_error",
            response.getRedirectedUrl()
        );
    }

    private MockHttpServletResponse execute(
        org.springframework.security.core.AuthenticationException exception
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        failureHandler.onAuthenticationFailure(
            request,
            response,
            exception
        );

        return response;
    }
}
