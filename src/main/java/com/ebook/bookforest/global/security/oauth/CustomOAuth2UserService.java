package com.ebook.bookforest.global.security.oauth;

import com.ebook.bookforest.domain.user.entity.User;
import com.ebook.bookforest.domain.user.service.OAuthService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuthService oAuthService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = super.loadUser(userRequest); // 사용자 정보 가져오기

        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        OAuth2Response response = createOAuth2Response(registrationId, oAuth2User.getAttributes());

        User user = loginOrSignUp(response);

        return new CustomOAuth2User(
            user.getId(),
            user.getRole(),
            response.getProvider(),
            oAuth2User.getAttributes()
        );
    }

    private OAuth2Response createOAuth2Response(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            // 키 값은 yml 에 등록(registrationId)
            case "kakao" -> new KakaoResponse(attributes);
            case "naver" -> new NaverResponse(attributes);
            default -> throw createAuthenticationException(
                "unsupported_provider",
                "지원하지 않는 소셜 로그인 제공자입니다."
            );
        };
    }

    private User loginOrSignUp(OAuth2Response response) {
        try {
            return oAuthService.loginOrSignUp(response);

        } catch (IllegalStateException exception) {
            throw createAuthenticationException(
                "inactive_user",
                "비활성화된 사용자입니다.",
                exception
            );

        } catch (IllegalArgumentException exception) {
            throw createAuthenticationException(
                "invalid_oauth_response",
                "소셜 로그인 사용자 정보가 올바르지 않습니다.",
                exception
            );
        }
    }

    private OAuth2AuthenticationException createAuthenticationException(
        String errorCode,
        String message
    ) {
        OAuth2Error error = new OAuth2Error(errorCode);

        return new OAuth2AuthenticationException(error,message);
    }

    private OAuth2AuthenticationException createAuthenticationException(
        String errorCode,
        String message,
        Throwable cause
    ) {
        OAuth2Error error = new OAuth2Error(errorCode);

        return new OAuth2AuthenticationException(error, cause);
    }

}
