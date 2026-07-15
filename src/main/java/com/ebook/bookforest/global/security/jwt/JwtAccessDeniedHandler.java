package com.ebook.bookforest.global.security.jwt;

import com.ebook.bookforest.global.exception.ErrorResponseWriter;
import com.ebook.bookforest.global.security.SecurityErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 인증: 통과 / 권한 : 부족 시 호출되는 핸들러
 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
        AccessDeniedException accessDeniedException) throws IOException, ServletException {

        errorResponseWriter.write(response, SecurityErrorCode.ACCESS_DENIED); // 403
    }
}
