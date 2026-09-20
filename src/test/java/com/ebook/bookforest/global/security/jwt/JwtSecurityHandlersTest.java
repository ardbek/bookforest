package com.ebook.bookforest.global.security.jwt;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ebook.bookforest.global.exception.ErrorResponseWriter;
import com.ebook.bookforest.global.security.SecurityErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class JwtSecurityHandlersTest {

    @Test
    void 인증되지_않은_요청은_AUTHENTICATION_REQUIRED로_처리한다()
        throws Exception {

        ErrorResponseWriter writer = mock(ErrorResponseWriter.class);
        JwtAuthenticationEntryPoint entryPoint =
            new JwtAuthenticationEntryPoint(writer);

        MockHttpServletResponse response =
            new MockHttpServletResponse();

        entryPoint.commence(
            new MockHttpServletRequest(),
            response,
            new BadCredentialsException("authentication required")
        );

        verify(writer).write(
            response,
            SecurityErrorCode.AUTHENTICATION_REQUIRED
        );
    }

    @Test
    void 권한이_부족한_요청은_ACCESS_DENIED로_처리한다()
        throws Exception {

        ErrorResponseWriter writer = mock(ErrorResponseWriter.class);
        JwtAccessDeniedHandler deniedHandler =
            new JwtAccessDeniedHandler(writer);

        MockHttpServletResponse response =
            new MockHttpServletResponse();

        deniedHandler.handle(
            new MockHttpServletRequest(),
            response,
            new AccessDeniedException("access denied")
        );

        verify(writer).write(
            response,
            SecurityErrorCode.ACCESS_DENIED
        );
    }
}
