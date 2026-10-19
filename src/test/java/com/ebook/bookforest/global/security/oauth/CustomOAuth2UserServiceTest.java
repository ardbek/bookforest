package com.ebook.bookforest.global.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ebook.bookforest.domain.user.entity.User;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.domain.user.exception.UserErrorCode;
import com.ebook.bookforest.domain.user.exception.UserException;
import com.ebook.bookforest.domain.user.service.OAuthService;
import com.ebook.bookforest.support.EntityIds;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestOperations;

/**
 * 소셜 로그인 사용자 처리 검증
 * 사용자 정보 API 응답은 mock으로 대신하고, 로그인 실패 사유가 프론트로 전달되는 OAuth2 오류 코드로 바뀌는지 확인
 */
class CustomOAuth2UserServiceTest {

    private static final Map<String, Object> KAKAO_ATTRIBUTES = Map.of(
        "id", 12345L,
        "kakao_account", Map.of("email", "reader@example.com", "profile", Map.of("nickname", "책벌레"))
    );

    private OAuthService oAuthService;
    private CustomOAuth2UserService customOAuth2UserService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        oAuthService = mock(OAuthService.class);
        customOAuth2UserService = new CustomOAuth2UserService(oAuthService);

        RestOperations restOperations = mock(RestOperations.class);
        given(restOperations.exchange(any(RequestEntity.class), any(ParameterizedTypeReference.class)))
            .willReturn(ResponseEntity.ok(KAKAO_ATTRIBUTES));
        customOAuth2UserService.setRestOperations(restOperations);
    }

    @Test
    @DisplayName("로그인 성공 시 사용자 ID, 권한, 소셜 식별값을 담은 인증 사용자 반환")
    void loadUserReturnsCustomOAuth2User() {
        // given
        given(oAuthService.loginOrSignUp(any())).willReturn(EntityIds.withId(User.create("책벌레", null, null), 7L));

        // when
        OAuth2User result = customOAuth2UserService.loadUser(userRequest("kakao"));

        // then
        assertThat(result).isInstanceOfSatisfying(CustomOAuth2User.class, user -> {
            assertThat(user.getUserId()).isEqualTo(7L);
            assertThat(user.getRole()).isEqualTo(UserRole.ROLE_USER);
            assertThat(user.getProvider()).isEqualTo(SocialProvider.KAKAO);
            assertThat(user.getProviderSubject()).isEqualTo("12345");
        });
    }

    @Test
    @DisplayName("지원하지 않는 소셜 플랫폼은 unsupported_provider")
    void unsupportedProvider() {
        assertErrorCode(() -> customOAuth2UserService.loadUser(userRequest("google")), "unsupported_provider");
    }

    @Test
    @DisplayName("연동 해제가 진행 중인 계정은 social_unlink_in_progress")
    void socialUnlinkInProgress() {
        given(oAuthService.loginOrSignUp(any())).willThrow(new UserException(UserErrorCode.SOCIAL_UNLINK_IN_PROGRESS));

        assertErrorCode(() -> customOAuth2UserService.loadUser(userRequest("kakao")), "social_unlink_in_progress");
    }

    @Test
    @DisplayName("비활성 사용자에 연결된 계정은 inactive_user")
    void inactiveUser() {
        given(oAuthService.loginOrSignUp(any())).willThrow(new IllegalStateException("비활성화된 사용자입니다."));

        assertErrorCode(() -> customOAuth2UserService.loadUser(userRequest("kakao")), "inactive_user");
    }

    @Test
    @DisplayName("소셜 사용자 정보가 올바르지 않으면 invalid_oauth_response")
    void invalidOAuthResponse() {
        given(oAuthService.loginOrSignUp(any())).willThrow(new IllegalArgumentException("식별값 없음"));

        assertErrorCode(() -> customOAuth2UserService.loadUser(userRequest("kakao")), "invalid_oauth_response");
    }

    private void assertErrorCode(Runnable loadUser, String errorCode) {
        assertThatThrownBy(loadUser::run)
            .isInstanceOfSatisfying(
                OAuth2AuthenticationException.class,
                exception -> assertThat(exception.getError().getErrorCode()).isEqualTo(errorCode)
            );
    }

    private OAuth2UserRequest userRequest(String registrationId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
            .clientId("test-client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://auth.example/authorize")
            .tokenUri("https://auth.example/token")
            .userInfoUri("https://auth.example/me")
            .userNameAttributeName("id")
            .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "social-access-token",
            Instant.now(),
            Instant.now().plusSeconds(60)
        );
        return new OAuth2UserRequest(registration, accessToken);
    }
}
