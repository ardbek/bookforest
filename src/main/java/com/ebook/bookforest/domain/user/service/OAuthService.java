package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.user.entity.SocialAccount;
import com.ebook.bookforest.domain.user.entity.User;
import com.ebook.bookforest.domain.user.repository.SocialAccountRepository;
import com.ebook.bookforest.domain.user.repository.UserRepository;
import com.ebook.bookforest.global.security.oauth.OAuth2Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;

    @Transactional
    public User loginOrSignUp(OAuth2Response response) {
        validateResponse(response);

        return socialAccountRepository.findBySocialProviderAndProviderSubject(
                response.getProvider(),
                response.getProviderSubject()
            ).map(account -> updateExistingUser(account, response))
            .orElseGet(() -> createNewUser(response));
    }

    /**
     * 사용자 정보 업데이트
     * @param account
     * @param response
     * @return
     */
    private User updateExistingUser(SocialAccount account, OAuth2Response response) {
        User user = account.getUser();

        if (!user.isActive()) {
            throw new IllegalStateException("비활성화된 사용자입니다.");
        }

        account.updateProfile(
            response.getEmail(),
            response.getNickname(),
            response.getProfileImageUrl()
        );

        user.updateProfile(
            response.getNickname(),
            response.getEmail(),
            response.getProfileImageUrl()
        );

        return user;
    }

    /**
     * 사용자 등록
     *
     * @param response
     * @return
     */
    private User createNewUser(OAuth2Response response) {
        // 사용자 객체 생성
        User user = User.builder()
            .displayName(response.getNickname())
            .email(response.getEmail())
            .profileImageUrl(response.getProfileImageUrl())
            .build();

        userRepository.save(user);

        // 소셜 로그인 계정 객체 생성
        SocialAccount socialAccount = SocialAccount.builder()
            .user(user)
            .socialProvider(response.getProvider())
            .providerSubject(response.getProviderSubject())
            .email(response.getEmail())
            .nickname(response.getNickname())
            .profileImageUrl(response.getProfileImageUrl())
            .build();

        socialAccountRepository.save(socialAccount);

        return user;
    }

    /**
     * OAuth2 응답 검증
     * @param response
     */
    private void validateResponse(OAuth2Response response) {
        if (response.getProviderSubject() == null || response.getProviderSubject().isBlank()) {
            log.error("OAuthService.validateResponse :: 소셜 로그인 사용자 식별값 없음");
            throw new IllegalArgumentException("소셜 로그인 사용자 식별값이 없습니다.");
        }

    }

}
