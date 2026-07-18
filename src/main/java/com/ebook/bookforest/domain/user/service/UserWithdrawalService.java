package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.auth.repository.RefreshTokenStore;
import com.ebook.bookforest.domain.user.event.SocialUnlinkTasksCreatedEvent;
import com.ebook.bookforest.domain.user.entity.User;
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

@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final FavoriteBookRepository favoriteBookRepository;
    private final FavoriteLibraryRepository favoriteLibraryRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialOAuthCredentialRepository credentialRepository;
    private final SocialUnlinkTaskCreationService taskCreationService;
    private final ApplicationEventPublisher eventPublisher;


    /**
     * 회원 탈퇴
     * @param userId
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() ->
                new UserException(UserErrorCode.USER_NOT_FOUND)
            );

        if (!user.isActive()) {
            throw new UserException(
                UserErrorCode.USER_ALREADY_WITHDRAWN
            );
        }

        LocalDateTime now = LocalDateTime.now();

        List<Long> taskIds = taskCreationService.createTasks(userId, now);

        // RT 삭제
        refreshTokenStore.deleteAllByUserId(userId);
        // 즐겨찾기 데이터 삭제
        favoriteBookRepository.deleteAllByUser_Id(userId);
        favoriteLibraryRepository.deleteAllByUser_Id(userId);
        // 외래키를 가진 Credential을 먼저 삭제
        credentialRepository.deleteAllBySocialAccount_User_Id(userId);
        credentialRepository.flush();

        // 소셜 로그인 계정 삭제
        socialAccountRepository.deleteAllByUser_Id(userId);
        socialAccountRepository.flush();

        user.withdraw(now);

        eventPublisher.publishEvent(
            new SocialUnlinkTasksCreatedEvent(taskIds)
        );

    }


}
