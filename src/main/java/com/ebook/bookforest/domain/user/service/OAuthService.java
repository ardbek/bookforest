package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.user.entity.SocialAccount;
import com.ebook.bookforest.domain.user.entity.User;
import com.ebook.bookforest.domain.user.enums.SocialUnlinkStatus;
import com.ebook.bookforest.domain.user.exception.UserErrorCode;
import com.ebook.bookforest.domain.user.exception.UserException;
import com.ebook.bookforest.domain.user.repository.SocialAccountRepository;
import com.ebook.bookforest.domain.user.repository.SocialUnlinkTaskRepository;
import com.ebook.bookforest.domain.user.repository.UserRepository;
import com.ebook.bookforest.global.security.oauth.OAuth2Response;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 사용자의 가입과 로그인 처리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    /** 연동 해제가 끝나지 않아 같은 소셜 계정의 재가입을 막는 작업 상태 */
    private static final Set<SocialUnlinkStatus> BLOCKING_STATUSES = Set.of(
        SocialUnlinkStatus.PENDING,
        SocialUnlinkStatus.PROCESSING,
        SocialUnlinkStatus.RETRY_WAITING
    );

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialUnlinkTaskRepository unlinkTaskRepository;

    /**
     * 소셜 로그인 처리(기존 계정은 프로필 갱신, 처음이면 가입)
     *
     * @throws IllegalArgumentException 소셜 사용자 식별값이 없는 경우
     * @throws UserException 같은 소셜 계정의 연동 해제가 아직 끝나지 않은 경우 SOCIAL_UNLINK_IN_PROGRESS
     */
    @Transactional
    public User loginOrSignUp(OAuth2Response response) {
        validateResponse(response);
        validateNotUnlinking(response);

        return socialAccountRepository.findBySocialProviderAndProviderSubject(
                response.getProvider(),
                response.getProviderSubject()
            ).map(account -> updateExistingUser(account, response))
            .orElseGet(() -> createNewUser(response));
    }

    private void validateNotUnlinking(OAuth2Response response) {
        boolean unlinking = unlinkTaskRepository
            .existsBySocialProviderAndProviderSubjectAndStatusIn(
                response.getProvider(),
                response.getProviderSubject(),
                BLOCKING_STATUSES
            );

        if (unlinking) {
            throw new UserException(UserErrorCode.SOCIAL_UNLINK_IN_PROGRESS);
        }
    }

    /** 탈퇴 시 소셜 계정을 함께 삭제하므로, 비활성 사용자에 연결된 계정은 데이터 불일치 */
    private User updateExistingUser(SocialAccount account, OAuth2Response response) {
        User user = account.getUser();

        if (!user.isActive()) {
            throw new IllegalStateException("비활성화된 사용자입니다.");
        }

        account.updateProfile(
            response.getNickname(),
            response.getEmail(),
            response.getProfileImageUrl()
        );

        user.updateProfile(
            response.getNickname(),
            response.getEmail(),
            response.getProfileImageUrl()
        );

        return user;
    }

    private User createNewUser(OAuth2Response response) {
        User user = User.create(
            response.getNickname(),
            response.getEmail(),
            response.getProfileImageUrl()
        );

        userRepository.save(user);

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

    private void validateResponse(OAuth2Response response) {
        if (response.getProviderSubject() == null || response.getProviderSubject().isBlank()) {
            log.error("소셜 로그인 응답에 사용자 식별값 없음: provider={}", response.getProvider());
            throw new IllegalArgumentException("소셜 로그인 사용자 식별값이 없습니다.");
        }

    }

}
