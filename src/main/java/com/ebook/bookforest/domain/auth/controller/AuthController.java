package com.ebook.bookforest.domain.auth.controller;

import com.ebook.bookforest.domain.auth.dto.ReissueResponse;
import com.ebook.bookforest.domain.auth.dto.ReissueResult;
import com.ebook.bookforest.domain.auth.exception.AuthErrorCode;
import com.ebook.bookforest.domain.auth.exception.AuthException;
import com.ebook.bookforest.domain.auth.service.AuthService;
import com.ebook.bookforest.global.security.oauth.OAuth2Properties;
import com.ebook.bookforest.global.security.oauth.RefreshTokenCookieManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Refresh Token 쿠키 기반 토큰 재발급, 로그아웃 API
 * 쿠키로 인증하는 요청이라 CSRF 방어를 위해 Origin 헤더를 프론트 주소와 대조
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuth2Properties properties;
    private final RefreshTokenCookieManager cookieManager;

    /**
     * 토큰 재발급
     * 재발급에 실패하면 더 쓸 수 없는 Refresh Token 쿠키를 만료시킨 뒤 오류 전달
     *
     * @throws AuthException Origin이 다르거나 쿠키가 없는 경우, 재발급에 실패한 경우
     */
    @PostMapping("/reissue")
    public ResponseEntity<ReissueResponse> reissue(HttpServletRequest request, HttpServletResponse response) {
        validateOrigin(request);
        String refreshToken = cookieManager.read(request)
            .orElseThrow(() -> new AuthException(AuthErrorCode.MISSING_REFRESH_TOKEN));

        try {
            ReissueResult result = authService.reissue(refreshToken);
            cookieManager.add(response, result.refreshToken(), result.refreshTokenExpiresAt());
            return ResponseEntity.ok(ReissueResponse.from(result));
        } catch (AuthException exception) {
            cookieManager.expire(response);
            throw exception;
        }
    }

    /**
     * 로그아웃
     *
     * @throws AuthException Origin이 다른 경우 INVALID_ORIGIN
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        validateOrigin(request);
        cookieManager.read(request).ifPresent(authService::logout);
        cookieManager.expire(response);
        return ResponseEntity.noContent().build();
    }

    private void validateOrigin(HttpServletRequest request) {
        String requestOrigin = request.getHeader(HttpHeaders.ORIGIN);
        String allowedOrigin = extractOrigin(properties.frontCallbackUrl());

        if (!allowedOrigin.equals(requestOrigin)) {
            throw new AuthException(AuthErrorCode.INVALID_ORIGIN);
        }
    }

    private String extractOrigin(String url) {
        URI uri = URI.create(url);
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}
