package com.ebook.bookforest.global.security.oauth;

import com.ebook.bookforest.domain.auth.dto.LoginSession;
import com.ebook.bookforest.domain.auth.service.AuthService;
import com.ebook.bookforest.domain.user.service.SocialOAuthCredentialService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * OAuth2 인증 성공 처리 핸들러
 */
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api";

    private final AuthService authService;
    private final OAuth2Properties properties;
    private final RefreshTokenCookieManager cookieManager;
    private final OAuth2AuthorizedClientRepository authorizedClientRepository;
    private final SocialOAuthCredentialService credentialService;

    public OAuth2AuthenticationSuccessHandler(
        AuthService authService,
        OAuth2Properties properties,
        RefreshTokenCookieManager cookieManager,
        OAuth2AuthorizedClientRepository authorizedClientRepository,
        SocialOAuthCredentialService credentialService
    ) {
        this.authService = authService;
        this.properties = properties;
        this.cookieManager = cookieManager;
        this.authorizedClientRepository = authorizedClientRepository;
        this.credentialService = credentialService;

        // 환경 변수에 등록된 콜백으로 이동
        setDefaultTargetUrl(properties.frontCallbackUrl());
        setAlwaysUseDefaultTargetUrl(true);
    }


    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
        Authentication authentication) throws IOException, ServletException {

        CustomOAuth2User oAuth2User = extractOAuth2User(authentication);

        OAuth2AuthenticationToken oauthToken = extractOAuth2AuthenticationToken(authentication);

        OAuth2AuthorizedClient authorizedClient =loadAuthorizedClient(oauthToken, request);

        /*
         * 네이버 토큰은 암호화
         * 카카오는 SocialOAuthCredentialService 안에서 바로 return
         */
        credentialService.saveOrUpdate(
            oAuth2User.getProvider(),
            oAuth2User.getProviderSubject(),
            authorizedClient.getAccessToken(),
            authorizedClient.getRefreshToken()
        );

        LoginSession loginSession = authService.createLoginSession(oAuth2User.getUserId());

        cookieManager.add(response, loginSession.refreshToken(), loginSession.refreshTokenExpiresAt());

        super.onAuthenticationSuccess(request, response, authentication);
    }

    /**
     * 소셜 로그인 인증 정보 OAuth2User 객체로 변환
     * @param authentication
     * @return
     */
    private CustomOAuth2User extractOAuth2User(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if(!(principal instanceof CustomOAuth2User oAuth2User)) {
            throw new IllegalArgumentException("OAuth2 인증 사용자 정보가 올바르지 않습니다.");
        }

        return oAuth2User;
    }

    /**
     * RT Cookie에 추가
     * @param response
     * @param loginSession
     */
    private void addRefreshTokenCookie(HttpServletResponse response, LoginSession loginSession) {
        Duration maxAge = Duration.between(Instant.now(), loginSession.refreshTokenExpiresAt());

        if (maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalStateException("Refresh Token 만료 시간이 올바르지 않습니다.");
        }

        ResponseCookie cookie = ResponseCookie
            .from(
                properties.refreshTokenCookieName(),
                loginSession.refreshToken()
            )
            .httpOnly(true)
            .secure(properties.cookieSecure())
            .sameSite(properties.cookieSameSite())
            .path(REFRESH_TOKEN_COOKIE_PATH)
            .maxAge(maxAge)
            .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

    }

    /**
     * OAuthToken 추출
     * @param authentication
     * @return
     */
    private OAuth2AuthenticationToken extractOAuth2AuthenticationToken(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            return oauthToken;
        }

        throw new IllegalArgumentException("OAuth2 인증 정보가 올바르지 않습니다.");
    }

    /**
     * OAuth2AuthorizedClient 조회
     * @param oauthToken
     * @param request
     * @return
     */
    private OAuth2AuthorizedClient loadAuthorizedClient(OAuth2AuthenticationToken oauthToken, HttpServletRequest request) {

        OAuth2AuthorizedClient authorizedClient =
            authorizedClientRepository.loadAuthorizedClient(
                oauthToken
                    .getAuthorizedClientRegistrationId(),
                oauthToken,
                request
            );

        if (authorizedClient == null) {
            throw new IllegalStateException("OAuth2 Authorized Client를 찾을 수 없습니다.");
        }

        return authorizedClient;
    }


}
