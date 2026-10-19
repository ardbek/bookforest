package com.ebook.bookforest.global.security.oauth;

import com.ebook.bookforest.domain.auth.dto.LoginSession;
import com.ebook.bookforest.domain.auth.service.AuthService;
import com.ebook.bookforest.domain.user.service.SocialOAuthCredentialService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인 성공 처리기
 * 소셜 플랫폼 토큰 저장, 로그인 세션 생성, Refresh Token 쿠키 발급 후 프론트 콜백 URL로 이동
 * Access Token은 여기서 주지 않고, 프론트가 콜백 후 재발급 API로 받음
 */
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;
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
        this.cookieManager = cookieManager;
        this.authorizedClientRepository = authorizedClientRepository;
        this.credentialService = credentialService;

        setDefaultTargetUrl(properties.frontCallbackUrl());
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {
        CustomOAuth2User oAuth2User = extractOAuth2User(authentication);
        OAuth2AuthenticationToken oauthToken = extractOAuth2AuthenticationToken(authentication);
        OAuth2AuthorizedClient authorizedClient = loadAuthorizedClient(oauthToken, request);

        // 플랫폼별 저장 여부와 암호화는 SocialOAuthCredentialService가 결정
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

    private CustomOAuth2User extractOAuth2User(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof CustomOAuth2User oAuth2User)) {
            throw new IllegalArgumentException("OAuth2 인증 사용자 정보가 올바르지 않습니다.");
        }
        return oAuth2User;
    }

    private OAuth2AuthenticationToken extractOAuth2AuthenticationToken(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            return oauthToken;
        }
        throw new IllegalArgumentException("OAuth2 인증 정보가 올바르지 않습니다.");
    }

    private OAuth2AuthorizedClient loadAuthorizedClient(
        OAuth2AuthenticationToken oauthToken,
        HttpServletRequest request
    ) {
        OAuth2AuthorizedClient authorizedClient = authorizedClientRepository.loadAuthorizedClient(
            oauthToken.getAuthorizedClientRegistrationId(),
            oauthToken,
            request
        );
        if (authorizedClient == null) {
            throw new IllegalStateException("OAuth2 Authorized Client를 찾을 수 없습니다.");
        }
        return authorizedClient;
    }
}
