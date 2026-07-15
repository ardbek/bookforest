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

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuth2Properties properties;
    private final RefreshTokenCookieManager cookieManager;

    /**
     * 토큰 재발급
     * @param request
     * @param response
     * @return
     */
    @PostMapping("/reissue")
    public ResponseEntity<ReissueResponse> reissue (HttpServletRequest request, HttpServletResponse response) {
        validateOrigin(request);

        String refreshToken = cookieManager
            .read(request)
            .orElseThrow(() -> new AuthException(AuthErrorCode.MISSING_REFRESH_TOKEN));

        try{
            ReissueResult result = authService.reissue(refreshToken);

            cookieManager.add(response, result.refreshToken(), result.refreshTokenExpiresAt());

            return ResponseEntity.ok(ReissueResponse.from(result));
        } catch (AuthException e) {
            cookieManager.expire(response);
            throw e;
        }
    }

    /**
     * 로그아웃
     * @param request
     * @param response
     * @return
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        validateOrigin(request);

        cookieManager
            .read(request)
            .ifPresent(authService::logout);

        cookieManager.expire(response);

        return ResponseEntity.noContent().build();
    }

    /**
     * 같은 사이트에서 요청된 쿠키인지 검즈
     * @param request
     */
    private void validateOrigin(
        HttpServletRequest request
    ) {
        String requestOrigin = request.getHeader(
            HttpHeaders.ORIGIN
        );

        String allowedOrigin = extractOrigin(
            properties.frontCallbackUrl()
        );

        if (!allowedOrigin.equals(requestOrigin)) {
            throw new AuthException(AuthErrorCode.INVALID_ORIGIN);
        }
    }

    private String extractOrigin(String url) {
        URI uri = URI.create(url);
        return uri.getScheme() + "://" + uri.getAuthority();
    }


}
