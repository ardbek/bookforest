package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.auth.repository.RefreshTokenStore;
import com.ebook.bookforest.domain.user.entity.User;
import com.ebook.bookforest.domain.user.exception.UserErrorCode;
import com.ebook.bookforest.domain.user.exception.UserException;
import com.ebook.bookforest.domain.user.repository.FavoriteBookRepository;
import com.ebook.bookforest.domain.user.repository.FavoriteLibraryRepository;
import com.ebook.bookforest.domain.user.repository.SocialAccountRepository;
import com.ebook.bookforest.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
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

        // RT 삭제
        refreshTokenStore.deleteAllByUserId(userId);
        // 즐겨찾기 데이터 삭제
        favoriteBookRepository.deleteAllByUser_Id(userId);
        favoriteLibraryRepository.deleteAllByUser_Id(userId);
        // 소셜 로그인 계정 삭제
        socialAccountRepository.deleteAllByUser_Id(userId);

        user.withdraw(LocalDateTime.now());

    }


}
