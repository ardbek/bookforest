package com.ebook.bookforest.global.security.jwt;

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

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtExceptionHandlerFilter extends OncePerRequestFilter {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {

        try {
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException e) {
            log.debug(
                "만료된 Access Token: exceptionType={}",
                e.getClass().getSimpleName()
            );

            errorResponseWriter.write(response, SecurityErrorCode.EXPIRED_ACCESS_TOKEN);
        } catch (JwtException exception) {
            log.debug(
                "잘못된 Access Token: exceptionType={}",
                exception.getClass().getSimpleName()
            );

            errorResponseWriter.write(
                response,
                SecurityErrorCode.INVALID_ACCESS_TOKEN
            );
        }
    }
}
