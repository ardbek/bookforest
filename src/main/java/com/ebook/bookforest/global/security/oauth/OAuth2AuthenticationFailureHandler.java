package com.ebook.bookforest.global.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 소셜 로그인 실패 처리기
 * 프론트 콜백 URL로 이동시키고 실패 사유를 error 쿼리 파라미터로 전달
 */
@Slf4j
@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final OAuth2Properties properties;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    public OAuth2AuthenticationFailureHandler(OAuth2Properties properties) {
        this.properties = properties;
    }

    @Override
    public void onAuthenticationFailure(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException {
        OAuth2LoginErrorCode errorCode = resolveErrorCode(exception);
        log.warn(
            "OAuth2 로그인 실패: errorCode={}, exceptionType={}",
            errorCode.code(),
            exception.getClass().getSimpleName()
        );

        redirectStrategy.sendRedirect(request, response, createFailureRedirectUrl(errorCode));
    }

    private OAuth2LoginErrorCode resolveErrorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oAuth2Exception) {
            return OAuth2LoginErrorCode.from(oAuth2Exception.getError().getErrorCode());
        }
        return OAuth2LoginErrorCode.OAUTH2_ERROR;
    }

    private String createFailureRedirectUrl(OAuth2LoginErrorCode errorCode) {
        return UriComponentsBuilder.fromUriString(properties.frontCallbackUrl())
            .queryParam("error", errorCode.code())
            .build()
            .encode()
            .toUriString();
    }
}
