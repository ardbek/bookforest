package com.ebook.bookforest.global.security.oauth;

import com.ebook.bookforest.domain.user.entity.User;
import com.ebook.bookforest.domain.user.exception.UserException;
import com.ebook.bookforest.domain.user.service.OAuthService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * 소셜 플랫폼 사용자 정보로 로그인 또는 회원 가입
 * 실패는 OAuth2 오류 코드로 바꿔 던지고, OAuth2AuthenticationFailureHandler가 프론트 콜백 URL의 error 값으로 전달
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuthService oAuthService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2Response response = createOAuth2Response(registrationId, oAuth2User.getAttributes());
        User user = loginOrSignUp(response);

        return new CustomOAuth2User(
            user.getId(),
            user.getRole(),
            response.getProvider(),
            response.getProviderSubject(),
            oAuth2User.getAttributes()
        );
    }

    // registrationId는 application.yml의 spring.security.oauth2.client.registration 키
    private OAuth2Response createOAuth2Response(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "kakao" -> new KakaoResponse(attributes);
            case "naver" -> new NaverResponse(attributes);
            default -> throw new OAuth2AuthenticationException(
                new OAuth2Error("unsupported_provider"),
                "지원하지 않는 소셜 로그인 제공자입니다."
            );
        };
    }

    private User loginOrSignUp(OAuth2Response response) {
        try {
            return oAuthService.loginOrSignUp(response);
        } catch (UserException exception) {
            // 사용자 오류 코드 문자열을 그대로 넘기고, 실패 처리기가 OAuth2LoginErrorCode로 변환(모르는 코드는 oauth2_error)
            throw toAuthenticationException(exception.getErrorCode().code(), exception);
        } catch (IllegalStateException exception) {
            throw toAuthenticationException("inactive_user", exception);
        } catch (IllegalArgumentException exception) {
            throw toAuthenticationException("invalid_oauth_response", exception);
        }
    }

    private OAuth2AuthenticationException toAuthenticationException(String errorCode, Throwable cause) {
        return new OAuth2AuthenticationException(new OAuth2Error(errorCode), cause);
    }
}
