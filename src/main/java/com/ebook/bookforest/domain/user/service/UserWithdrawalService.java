package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.user.entity.User;
import com.ebook.bookforest.domain.user.event.SocialUnlinkTasksCreatedEvent;
import com.ebook.bookforest.domain.user.event.UserWithdrawnEvent;
import com.ebook.bookforest.domain.user.exception.UserErrorCode;
import com.ebook.bookforest.domain.user.exception.UserException;
import com.ebook.bookforest.domain.user.repository.FavoriteBookRepository;
import com.ebook.bookforest.domain.user.repository.FavoriteLibraryRepository;
import com.ebook.bookforest.domain.user.repository.SocialAccountRepository;
import com.ebook.bookforest.domain.user.repository.SocialOAuthCredentialRepository;
import com.ebook.bookforest.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 서비스
 * 소셜 연동 해제는 외부 API 호출이라 작업만 기록하고, 탈퇴 커밋 후 이벤트로 처리
 * 로그인 세션은 인증 도메인이 탈퇴 이벤트를 받아 커밋 후 삭제
 */
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final UserRepository userRepository;
    private final FavoriteBookRepository favoriteBookRepository;
    private final FavoriteLibraryRepository favoriteLibraryRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialOAuthCredentialRepository credentialRepository;
    private final SocialUnlinkTaskCreationService taskCreationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 회원 탈퇴
     * 즐겨찾기, 소셜 계정을 삭제하고 사용자는 개인정보를 지운 비활성 상태로 보존
     *
     * @throws UserException 사용자가 없는 경우 USER_NOT_FOUND, 이미 탈퇴한 경우 USER_ALREADY_WITHDRAWN
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new UserException(UserErrorCode.USER_ALREADY_WITHDRAWN);
        }

        LocalDateTime now = LocalDateTime.now();
        List<Long> taskIds = taskCreationService.createTasks(userId, now);

        favoriteBookRepository.deleteAllByUser_Id(userId);
        favoriteLibraryRepository.deleteAllByUser_Id(userId);

        // 소셜 계정을 참조하는 Credential을 먼저 삭제해야 외래키 오류가 나지 않음
        credentialRepository.deleteAllBySocialAccount_User_Id(userId);
        credentialRepository.flush();
        socialAccountRepository.deleteAllByUser_Id(userId);
        socialAccountRepository.flush();

        user.withdraw(now);

        eventPublisher.publishEvent(new SocialUnlinkTasksCreatedEvent(taskIds));
        eventPublisher.publishEvent(new UserWithdrawnEvent(userId));
    }
}
