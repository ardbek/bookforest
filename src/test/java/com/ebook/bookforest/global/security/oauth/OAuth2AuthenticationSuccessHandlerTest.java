package com.ebook.bookforest.global.security.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ebook.bookforest.domain.auth.dto.LoginSession;
import com.ebook.bookforest.domain.auth.service.AuthService;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.domain.user.service.SocialOAuthCredentialService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

class OAuth2AuthenticationSuccessHandlerTest {

    private static final Long USER_ID = 1L;
    private static final String CALLBACK_URL =
        "http://localhost:3000/auth/callback";
    private static final String COOKIE_NAME = "refresh_token";
    private static final String BOOKFOREST_REFRESH_TOKEN =
        "bookforest-refresh-token";
    private static final String NAVER_PROVIDER_SUBJECT = "naver-user-id";
    private static final String KAKAO_PROVIDER_SUBJECT = "kakao-user-id";

    private AuthService authService;
    private OAuth2AuthorizedClientRepository authorizedClientRepository;
    private SocialOAuthCredentialService credentialService;
    private OAuth2AuthorizedClient authorizedClient;
    private OAuth2AccessToken providerAccessToken;
    private OAuth2RefreshToken providerRefreshToken;
    private OAuth2AuthenticationSuccessHandler successHandler;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        authorizedClientRepository =
            mock(OAuth2AuthorizedClientRepository.class);
        credentialService =
            mock(SocialOAuthCredentialService.class);
        authorizedClient = mock(OAuth2AuthorizedClient.class);

        Instant issuedAt = Instant.parse("2026-07-16T00:00:00Z");
        providerAccessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "provider-access-token",
            issuedAt,
            issuedAt.plusSeconds(3600)
        );
        providerRefreshToken = new OAuth2RefreshToken(
            "provider-refresh-token",
            issuedAt
        );

        when(authorizedClient.getAccessToken())
            .thenReturn(providerAccessToken);
        when(authorizedClient.getRefreshToken())
            .thenReturn(providerRefreshToken);
        when(authorizedClientRepository.loadAuthorizedClient(
            anyString(),
            any(OAuth2AuthenticationToken.class),
            any(HttpServletRequest.class)
        )).thenReturn(authorizedClient);

        OAuth2Properties properties = new OAuth2Properties(
            CALLBACK_URL,
            COOKIE_NAME,
            false,
            "Lax"
        );
        RefreshTokenCookieManager cookieManager =
            new RefreshTokenCookieManager(properties);

        successHandler = new OAuth2AuthenticationSuccessHandler(
            authService,
            properties,
            cookieManager,
            authorizedClientRepository,
            credentialService
        );
    }

    @Test
    void OAuth2_로그인에_성공하면_소셜_토큰을_전달하고_콜백으로_이동한다()
        throws Exception {

        Instant expiresAt = Instant.now().plus(Duration.ofDays(14));
        when(authService.createLoginSession(USER_ID))
            .thenReturn(new LoginSession(
                BOOKFOREST_REFRESH_TOKEN,
                expiresAt
            ));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationToken authentication =
            authenticationWithCustomOAuth2User(SocialProvider.NAVER);

        successHandler.onAuthenticationSuccess(
            request,
            response,
            authentication
        );

        assertEquals(CALLBACK_URL, response.getRedirectedUrl());
        assertEquals(302, response.getStatus());

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith(
            COOKIE_NAME + "=" + BOOKFOREST_REFRESH_TOKEN
        ));
        assertTrue(setCookie.contains("Path=/api"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        assertTrue(setCookie.contains("Max-Age="));
        assertFalse(setCookie.contains("Secure"));

        verify(authorizedClientRepository).loadAuthorizedClient(
            "naver",
            authentication,
            request
        );
        verify(credentialService).saveOrUpdate(
            SocialProvider.NAVER,
            NAVER_PROVIDER_SUBJECT,
            providerAccessToken,
            providerRefreshToken
        );
        verify(authService).createLoginSession(USER_ID);
    }

    @Test
    void 카카오_로그인도_소셜_토큰_저장_서비스에_전달한다()
        throws Exception {

        when(authService.createLoginSession(USER_ID))
            .thenReturn(new LoginSession(
                BOOKFOREST_REFRESH_TOKEN,
                Instant.now().plus(Duration.ofDays(14))
            ));

        OAuth2AuthenticationToken authentication =
            authenticationWithCustomOAuth2User(SocialProvider.KAKAO);

        successHandler.onAuthenticationSuccess(
            new MockHttpServletRequest(),
            new MockHttpServletResponse(),
            authentication
        );

        verify(credentialService).saveOrUpdate(
            SocialProvider.KAKAO,
            KAKAO_PROVIDER_SUBJECT,
            providerAccessToken,
            providerRefreshToken
        );
    }

    @Test
    void 운영_쿠키_설정이면_Secure와_SameSite_None을_사용한다()
        throws Exception {

        OAuth2Properties secureProperties = new OAuth2Properties(
            "https://bookforest.cc/auth/callback",
            COOKIE_NAME,
            true,
            "None"
        );
        RefreshTokenCookieManager secureCookieManager =
            new RefreshTokenCookieManager(secureProperties);
        OAuth2AuthenticationSuccessHandler secureHandler =
            new OAuth2AuthenticationSuccessHandler(
                authService,
                secureProperties,
                secureCookieManager,
                authorizedClientRepository,
                credentialService
            );

        when(authService.createLoginSession(USER_ID))
            .thenReturn(new LoginSession(
                BOOKFOREST_REFRESH_TOKEN,
                Instant.now().plus(Duration.ofDays(14))
            ));

        MockHttpServletResponse response = new MockHttpServletResponse();

        secureHandler.onAuthenticationSuccess(
            new MockHttpServletRequest(),
            response,
            authenticationWithCustomOAuth2User(SocialProvider.KAKAO)
        );

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("SameSite=None"));
        assertEquals(
            "https://bookforest.cc/auth/callback",
            response.getRedirectedUrl()
        );
    }

    @Test
    void 인증_Principal이_CustomOAuth2User가_아니면_로그인_세션을_만들지_않는다() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn("wrong-principal");

        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(
            IllegalArgumentException.class,
            () -> successHandler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authentication
            )
        );

        verifyNoInteractions(authService, credentialService);
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
        assertNull(response.getRedirectedUrl());
    }

    @Test
    void 인증_객체가_OAuth2AuthenticationToken이_아니면_로그인하지_않는다() {
        CustomOAuth2User principal = customOAuth2User(SocialProvider.KAKAO);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);

        assertThrows(
            IllegalArgumentException.class,
            () -> successHandler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                authentication
            )
        );

        verifyNoInteractions(authService, credentialService);
    }

    @Test
    void OAuth2AuthorizedClient가_없으면_소셜_토큰과_로그인_세션을_만들지_않는다() {
        when(authorizedClientRepository.loadAuthorizedClient(
            anyString(),
            any(OAuth2AuthenticationToken.class),
            any(HttpServletRequest.class)
        )).thenReturn(null);

        OAuth2AuthenticationToken authentication =
            authenticationWithCustomOAuth2User(SocialProvider.NAVER);

        assertThrows(
            IllegalStateException.class,
            () -> successHandler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                authentication
            )
        );

        verifyNoInteractions(authService, credentialService);
    }

    @Test
    void 이미_만료된_Bookforest_Refresh_Token이면_쿠키와_redirect를_만들지_않는다() {
        when(authService.createLoginSession(USER_ID))
            .thenReturn(new LoginSession(
                BOOKFOREST_REFRESH_TOKEN,
                Instant.now().minusSeconds(1)
            ));

        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(
            IllegalArgumentException.class,
            () -> successHandler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authenticationWithCustomOAuth2User(SocialProvider.KAKAO)
            )
        );

        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
        assertNull(response.getRedirectedUrl());
    }

    private OAuth2AuthenticationToken authenticationWithCustomOAuth2User(
        SocialProvider provider
    ) {
        CustomOAuth2User principal = customOAuth2User(provider);

        return new OAuth2AuthenticationToken(
            principal,
            principal.getAuthorities(),
            registrationId(provider)
        );
    }

    private CustomOAuth2User customOAuth2User(SocialProvider provider) {
        String providerSubject = provider == SocialProvider.NAVER
            ? NAVER_PROVIDER_SUBJECT
            : KAKAO_PROVIDER_SUBJECT;

        return new CustomOAuth2User(
            USER_ID,
            UserRole.ROLE_USER,
            provider,
            providerSubject,
            Map.of("id", providerSubject)
        );
    }

    private String registrationId(SocialProvider provider) {
        return provider == SocialProvider.NAVER
            ? "naver"
            : "kakao";
    }
}
