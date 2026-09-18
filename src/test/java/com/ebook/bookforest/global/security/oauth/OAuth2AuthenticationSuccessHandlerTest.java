package com.ebook.bookforest.global.security.oauth;

import com.ebook.bookforest.domain.auth.dto.LoginSession;
import com.ebook.bookforest.domain.auth.service.AuthService;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.domain.user.service.SocialOAuthCredentialService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OAuth2AuthenticationSuccessHandlerTest {
    private final AuthService auth = mock(AuthService.class);
    private final SocialOAuthCredentialService credentials = mock(SocialOAuthCredentialService.class);
    private final OAuth2AuthorizedClientRepository clients = mock(OAuth2AuthorizedClientRepository.class);
    private final OAuth2Properties properties = new OAuth2Properties(
        "https://frontend.example/auth/callback", "refresh_token", true, "Lax");
    private final OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
        auth, properties, new RefreshTokenCookieManager(properties), clients, credentials);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final CustomOAuth2User user = new CustomOAuth2User(
        7L, UserRole.ROLE_USER, SocialProvider.NAVER, "provider-subject", Map.of());
    private final OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
        user, user.getAuthorities(), "naver");
    private final OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
    private final OAuth2RefreshToken refreshToken = mock(OAuth2RefreshToken.class);

    @Test
    void callbackStoresCredentialsIssuesOneCookieAndRedirectsToConfiguredUrl() throws Exception {
        authorizeClient();
        when(auth.createLoginSession(7L)).thenReturn(new LoginSession("issued-refresh", Instant.now().plusSeconds(3600)));

        handler.onAuthenticationSuccess(request, response, authentication);

        var order = inOrder(credentials, auth);
        order.verify(credentials).saveOrUpdate(SocialProvider.NAVER, "provider-subject", accessToken, refreshToken);
        order.verify(auth).createLoginSession(7L);
        assertEquals(properties.frontCallbackUrl(), response.getRedirectedUrl());
        assertEquals(302, response.getStatus());
        assertEquals(1, response.getHeaders(HttpHeaders.SET_COOKIE).size());
        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.startsWith("refresh_token=issued-refresh;"));
        assertTrue(cookie.contains("Path=/api"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.matches(".*Max-Age=[1-9][0-9]*;.*"));
    }

    @Test
    void credentialFailureDoesNotIssueSessionCookieOrRedirect() {
        authorizeClient();
        doThrow(new IllegalStateException("credential storage unavailable")).when(credentials)
            .saveOrUpdate(SocialProvider.NAVER, "provider-subject", accessToken, refreshToken);

        assertThrows(IllegalStateException.class,
            () -> handler.onAuthenticationSuccess(request, response, authentication));

        verifyNoInteractions(auth);
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
        assertNull(response.getRedirectedUrl());
    }

    @Test
    void absentAuthorizedClientDoesNotStoreCredentialsOrIssueSession() {
        assertThrows(IllegalStateException.class,
            () -> handler.onAuthenticationSuccess(request, response, authentication));
        verifyNoInteractions(credentials, auth);
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    private void authorizeClient() {
        var authorized = mock(OAuth2AuthorizedClient.class);
        when(authorized.getAccessToken()).thenReturn(accessToken);
        when(authorized.getRefreshToken()).thenReturn(refreshToken);
        when(clients.loadAuthorizedClient("naver", authentication, request)).thenReturn(authorized);
    }
}
