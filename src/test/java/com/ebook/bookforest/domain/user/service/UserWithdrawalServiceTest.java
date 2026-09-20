package com.ebook.bookforest.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ebook.bookforest.domain.auth.repository.RefreshTokenStore;
import com.ebook.bookforest.domain.user.entity.User;
import com.ebook.bookforest.domain.user.exception.UserErrorCode;
import com.ebook.bookforest.domain.user.exception.UserException;
import com.ebook.bookforest.domain.user.repository.FavoriteBookRepository;
import com.ebook.bookforest.domain.user.repository.FavoriteLibraryRepository;
import com.ebook.bookforest.domain.user.repository.SocialAccountRepository;
import com.ebook.bookforest.domain.user.repository.SocialOAuthCredentialRepository;
import com.ebook.bookforest.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UserWithdrawalServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private FavoriteBookRepository favoriteBookRepository;

    @Mock
    private FavoriteLibraryRepository favoriteLibraryRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private SocialOAuthCredentialRepository credentialRepository;

    @Mock
    private SocialUnlinkTaskCreationService taskCreationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserWithdrawalService userWithdrawalService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
            .id(USER_ID)
            .displayName("책벌레")
            .email("reader@example.com")
            .profileImageUrl("https://example.com/profile.png")
            .active(true)
            .build();
    }

    @Test
    void 회원_탈퇴시_세션과_연관_데이터를_삭제하고_개인정보를_익명화한다() {
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(activeUser));
        when(taskCreationService.createTasks(
            org.mockito.ArgumentMatchers.eq(USER_ID),
            org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(java.util.List.of(10L));
        LocalDateTime beforeWithdrawal = LocalDateTime.now();

        userWithdrawalService.withdraw(USER_ID);

        LocalDateTime afterWithdrawal = LocalDateTime.now();
        verify(refreshTokenStore).deleteAllByUserId(USER_ID);
        verify(favoriteBookRepository).deleteAllByUser_Id(USER_ID);
        verify(favoriteLibraryRepository).deleteAllByUser_Id(USER_ID);
        verify(credentialRepository).deleteAllBySocialAccount_User_Id(USER_ID);
        verify(credentialRepository).flush();
        verify(socialAccountRepository).deleteAllByUser_Id(USER_ID);
        verify(socialAccountRepository).flush();
        verify(eventPublisher).publishEvent(
            org.mockito.ArgumentMatchers.any(
                com.ebook.bookforest.domain.user.event.SocialUnlinkTasksCreatedEvent.class
            )
        );
        verify(userRepository, never()).save(activeUser);

        InOrder deleteOrder = inOrder(
            credentialRepository,
            socialAccountRepository
        );
        deleteOrder.verify(credentialRepository)
            .deleteAllBySocialAccount_User_Id(USER_ID);
        deleteOrder.verify(credentialRepository).flush();
        deleteOrder.verify(socialAccountRepository)
            .deleteAllByUser_Id(USER_ID);

        assertFalse(activeUser.isActive());
        assertNull(activeUser.getDisplayName());
        assertNull(activeUser.getEmail());
        assertNull(activeUser.getProfileImageUrl());
        assertNotNull(activeUser.getWithdrawn());
        assertTrue(
            !activeUser.getWithdrawn().isBefore(beforeWithdrawal)
                && !activeUser.getWithdrawn().isAfter(afterWithdrawal)
        );
    }

    @Test
    void 존재하지_않는_사용자는_탈퇴할_수_없다() {
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.empty());

        UserException exception = assertThrows(
            UserException.class,
            () -> userWithdrawalService.withdraw(USER_ID)
        );

        assertEquals(
            UserErrorCode.USER_NOT_FOUND,
            exception.getErrorCode()
        );
        verifyNoInteractions(
            refreshTokenStore,
            favoriteBookRepository,
            favoriteLibraryRepository,
            socialAccountRepository,
            credentialRepository,
            taskCreationService,
            eventPublisher
        );
    }

    @Test
    void 이미_탈퇴한_사용자는_다시_탈퇴할_수_없다() {
        User withdrawnUser = User.builder()
            .id(USER_ID)
            .active(false)
            .withdrawn(LocalDateTime.now().minusDays(1))
            .build();
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(withdrawnUser));

        UserException exception = assertThrows(
            UserException.class,
            () -> userWithdrawalService.withdraw(USER_ID)
        );

        assertEquals(
            UserErrorCode.USER_ALREADY_WITHDRAWN,
            exception.getErrorCode()
        );
        verifyNoInteractions(
            refreshTokenStore,
            favoriteBookRepository,
            favoriteLibraryRepository,
            socialAccountRepository,
            credentialRepository,
            taskCreationService,
            eventPublisher
        );
    }
}
