package com.ebook.bookforest.global.security.jwt;

import com.ebook.bookforest.global.exception.ErrorResponseWriter;
import com.ebook.bookforest.global.security.SecurityErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 인증 없이 로그인 필요 경로에 접근한 요청 처리기
 * 로그인 페이지로 이동시키지 않고 authentication_required 오류 응답
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException {
        errorResponseWriter.write(response, SecurityErrorCode.AUTHENTICATION_REQUIRED);
    }
}
