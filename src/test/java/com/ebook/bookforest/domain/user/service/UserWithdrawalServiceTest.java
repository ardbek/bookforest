package com.ebook.bookforest.domain.user.service;

import static com.ebook.bookforest.support.EntityIds.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.ebook.bookforest.domain.user.entity.User;
import com.ebook.bookforest.domain.user.event.UserWithdrawnEvent;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UserWithdrawalServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

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
        activeUser = withId(
            User.create("책벌레", "reader@example.com", "https://example.com/profile.png"),
            USER_ID
        );
    }

    @Test
    void 회원_탈퇴시_연관_데이터를_삭제하고_개인정보를_익명화한_뒤_탈퇴_이벤트를_발행한다() {
        given(userRepository.findById(USER_ID))
            .willReturn(Optional.of(activeUser));
        given(taskCreationService.createTasks(
            org.mockito.ArgumentMatchers.eq(USER_ID),
            org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).willReturn(java.util.List.of(10L));
        LocalDateTime beforeWithdrawal = LocalDateTime.now();

        userWithdrawalService.withdraw(USER_ID);

        LocalDateTime afterWithdrawal = LocalDateTime.now();
        then(favoriteBookRepository).should().deleteAllByUser_Id(USER_ID);
        then(favoriteLibraryRepository).should().deleteAllByUser_Id(USER_ID);
        then(credentialRepository).should().deleteAllBySocialAccount_User_Id(USER_ID);
        then(credentialRepository).should().flush();
        then(socialAccountRepository).should().deleteAllByUser_Id(USER_ID);
        then(socialAccountRepository).should().flush();
        then(eventPublisher).should().publishEvent(
            org.mockito.ArgumentMatchers.any(
                com.ebook.bookforest.domain.user.event.SocialUnlinkTasksCreatedEvent.class
            )
        );
        then(eventPublisher).should().publishEvent(new UserWithdrawnEvent(USER_ID));
        then(userRepository).should(never()).save(activeUser);

        InOrder deleteOrder = inOrder(credentialRepository, socialAccountRepository);
        then(credentialRepository).should(deleteOrder).deleteAllBySocialAccount_User_Id(USER_ID);
        then(credentialRepository).should(deleteOrder).flush();
        then(socialAccountRepository).should(deleteOrder).deleteAllByUser_Id(USER_ID);

        assertThat(activeUser.isActive()).isFalse();
        assertThat(activeUser.getDisplayName()).isNull();
        assertThat(activeUser.getEmail()).isNull();
        assertThat(activeUser.getProfileImageUrl()).isNull();
        assertThat(activeUser.getWithdrawnAt()).isNotNull();
        assertThat(activeUser.getWithdrawnAt()).isBetween(beforeWithdrawal, afterWithdrawal);
    }

    @Test
    void 존재하지_않는_사용자는_탈퇴할_수_없다() {
        given(userRepository.findById(USER_ID))
            .willReturn(Optional.empty());

        UserException exception = assertThatExceptionOfType(UserException.class)
            .isThrownBy(() -> userWithdrawalService.withdraw(USER_ID))
            .actual();

        assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        then(favoriteBookRepository).shouldHaveNoInteractions();
        then(favoriteLibraryRepository).shouldHaveNoInteractions();
        then(socialAccountRepository).shouldHaveNoInteractions();
        then(credentialRepository).shouldHaveNoInteractions();
        then(taskCreationService).shouldHaveNoInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void 이미_탈퇴한_사용자는_다시_탈퇴할_수_없다() {
        User withdrawnUser = withId(User.create("탈퇴자", null, null), USER_ID);
        withdrawnUser.withdraw(LocalDateTime.now().minusDays(1));
        given(userRepository.findById(USER_ID))
            .willReturn(Optional.of(withdrawnUser));

        UserException exception = assertThatExceptionOfType(UserException.class)
            .isThrownBy(() -> userWithdrawalService.withdraw(USER_ID))
            .actual();

        assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_ALREADY_WITHDRAWN);
        then(favoriteBookRepository).shouldHaveNoInteractions();
        then(favoriteLibraryRepository).shouldHaveNoInteractions();
        then(socialAccountRepository).shouldHaveNoInteractions();
        then(credentialRepository).shouldHaveNoInteractions();
        then(taskCreationService).shouldHaveNoInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
    }
}
