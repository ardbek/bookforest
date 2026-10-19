package com.ebook.bookforest.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.ebook.bookforest.domain.auth.event.UserWithdrawnEventListener;
import com.ebook.bookforest.domain.auth.repository.RefreshTokenStore;
import com.ebook.bookforest.support.MySqlDataJpaTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원 탈퇴 후 로그인 세션 삭제 시점 검증
 * 커밋 후 이벤트는 Mockito 단위 테스트로 확인할 수 없어(12.2), 테스트 트랜잭션을 끄고 실제 커밋과 롤백으로 확인
 */
@MySqlDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({UserWithdrawalService.class, SocialUnlinkTaskCreationService.class, UserWithdrawnEventListener.class})
class UserWithdrawalServiceIntegrationTest {

    private static final Long USER_ID = 501L;

    @Autowired
    private UserWithdrawalService userWithdrawalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @BeforeEach
    void setUp() {
        deleteUser();
        jdbcTemplate.update(
            "insert into users (user_id, is_active, role, display_name) values (?, 1, 'ROLE_USER', '책벌레')",
            USER_ID
        );
    }

    @AfterEach
    void tearDown() {
        deleteUser();
    }

    @Test
    @DisplayName("탈퇴 트랜잭션이 커밋된 뒤에 로그인 세션 삭제")
    void withdrawDeletesSessionsAfterCommit() {
        // when
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            userWithdrawalService.withdraw(USER_ID);
            then(refreshTokenStore).shouldHaveNoInteractions();
        });

        // then
        then(refreshTokenStore).should().deleteAllByUserId(USER_ID);
        assertThat(isActive()).isFalse();
    }

    @Test
    @DisplayName("탈퇴 트랜잭션이 롤백되면 로그인 세션을 지우지 않음")
    void withdrawKeepsSessionsWhenRolledBack() {
        // when
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            userWithdrawalService.withdraw(USER_ID);
            status.setRollbackOnly();
        });

        // then
        then(refreshTokenStore).shouldHaveNoInteractions();
        assertThat(isActive()).isTrue();
    }

    @Test
    @DisplayName("세션 삭제에 실패해도 탈퇴는 완료된 상태로 정상 종료")
    void withdrawSucceedsWhenSessionDeletionFails() {
        // given
        willThrow(new IllegalStateException("Redis 연결 실패")).given(refreshTokenStore).deleteAllByUserId(USER_ID);

        // when
        userWithdrawalService.withdraw(USER_ID);

        // then
        assertThat(isActive()).isFalse();
    }

    private boolean isActive() {
        return Boolean.TRUE.equals(
            jdbcTemplate.queryForObject("select is_active from users where user_id = ?", Boolean.class, USER_ID)
        );
    }

    private void deleteUser() {
        jdbcTemplate.update("delete from users where user_id = ?", USER_ID);
    }
}
