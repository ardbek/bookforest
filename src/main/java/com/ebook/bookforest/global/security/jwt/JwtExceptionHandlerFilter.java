package com.ebook.bookforest.global.security.jwt;

import com.ebook.bookforest.domain.auth.exception.AuthException;
import com.ebook.bookforest.global.exception.ErrorResponseWriter;
import com.ebook.bookforest.global.security.SecurityErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JwtAuthenticationFilter에서 생긴 토큰, 사용자 오류를 공통 오류 응답으로 변환하는 필터
 * MVC 밖이라 GlobalExceptionHandler가 처리하지 못하는 예외 담당
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtExceptionHandlerFilter extends OncePerRequestFilter {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException exception) {
            log.debug("만료된 Access Token: exceptionType={}", exception.getClass().getSimpleName());
            errorResponseWriter.write(response, SecurityErrorCode.EXPIRED_ACCESS_TOKEN);
        } catch (JwtException exception) {
            log.debug("잘못된 Access Token: exceptionType={}", exception.getClass().getSimpleName());
            errorResponseWriter.write(response, SecurityErrorCode.INVALID_ACCESS_TOKEN);
        } catch (AuthException exception) {
            log.debug("비활성 사용자의 Access Token 요청: errorCode={}", exception.getErrorCode().code());
            errorResponseWriter.write(response, exception.getErrorCode());
        }
    }
}
