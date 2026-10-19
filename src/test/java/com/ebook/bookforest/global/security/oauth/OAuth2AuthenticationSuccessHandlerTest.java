package com.ebook.bookforest.global.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

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

        given(authorizedClient.getAccessToken())
            .willReturn(providerAccessToken);
        given(authorizedClient.getRefreshToken())
            .willReturn(providerRefreshToken);
        given(authorizedClientRepository.loadAuthorizedClient(
            anyString(),
            any(OAuth2AuthenticationToken.class),
            any(HttpServletRequest.class)
        )).willReturn(authorizedClient);

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
        given(authService.createLoginSession(USER_ID))
            .willReturn(new LoginSession(
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

        assertThat(response.getRedirectedUrl()).isEqualTo(CALLBACK_URL);
        assertThat(response.getStatus()).isEqualTo(302);

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).startsWith(COOKIE_NAME + "=" + BOOKFOREST_REFRESH_TOKEN);
        assertThat(setCookie).contains("Path=/api");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(setCookie).contains("Max-Age=");
        assertThat(setCookie).doesNotContain("Secure");

        then(authorizedClientRepository).should().loadAuthorizedClient(
            "naver",
            authentication,
            request
        );
        then(credentialService).should().saveOrUpdate(
            SocialProvider.NAVER,
            NAVER_PROVIDER_SUBJECT,
            providerAccessToken,
            providerRefreshToken
        );
        then(authService).should().createLoginSession(USER_ID);
    }

    @Test
    void 카카오_로그인도_소셜_토큰_저장_서비스에_전달한다()
        throws Exception {

        given(authService.createLoginSession(USER_ID))
            .willReturn(new LoginSession(
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

        then(credentialService).should().saveOrUpdate(
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

        given(authService.createLoginSession(USER_ID))
            .willReturn(new LoginSession(
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
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).contains("SameSite=None");
        assertThat(response.getRedirectedUrl()).isEqualTo("https://bookforest.cc/auth/callback");
    }

    @Test
    void 인증_Principal이_CustomOAuth2User가_아니면_로그인_세션을_만들지_않는다() {
        Authentication authentication = mock(Authentication.class);
        given(authentication.getPrincipal()).willReturn("wrong-principal");

        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> successHandler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authentication
            )).isInstanceOf(IllegalArgumentException.class);

        then(authService).shouldHaveNoInteractions();
        then(credentialService).shouldHaveNoInteractions();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    void 인증_객체가_OAuth2AuthenticationToken이_아니면_로그인하지_않는다() {
        CustomOAuth2User principal = customOAuth2User(SocialProvider.KAKAO);
        Authentication authentication = mock(Authentication.class);
        given(authentication.getPrincipal()).willReturn(principal);

        assertThatThrownBy(() -> successHandler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                authentication
            )).isInstanceOf(IllegalArgumentException.class);

        then(authService).shouldHaveNoInteractions();
        then(credentialService).shouldHaveNoInteractions();
    }

    @Test
    void OAuth2AuthorizedClient가_없으면_소셜_토큰과_로그인_세션을_만들지_않는다() {
        given(authorizedClientRepository.loadAuthorizedClient(
            anyString(),
            any(OAuth2AuthenticationToken.class),
            any(HttpServletRequest.class)
        )).willReturn(null);

        OAuth2AuthenticationToken authentication =
            authenticationWithCustomOAuth2User(SocialProvider.NAVER);

        assertThatThrownBy(() -> successHandler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                authentication
            )).isInstanceOf(IllegalStateException.class);

        then(authService).shouldHaveNoInteractions();
        then(credentialService).shouldHaveNoInteractions();
    }

    @Test
    void 이미_만료된_Bookforest_Refresh_Token이면_쿠키와_redirect를_만들지_않는다() {
        given(authService.createLoginSession(USER_ID))
            .willReturn(new LoginSession(
                BOOKFOREST_REFRESH_TOKEN,
                Instant.now().minusSeconds(1)
            ));

        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> successHandler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                response,
                authenticationWithCustomOAuth2User(SocialProvider.KAKAO)
            )).isInstanceOf(IllegalArgumentException.class);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(response.getRedirectedUrl()).isNull();
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
