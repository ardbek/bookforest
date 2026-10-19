package com.ebook.bookforest.domain.auth.event;

import com.ebook.bookforest.domain.auth.repository.RefreshTokenStore;
import com.ebook.bookforest.domain.user.event.UserWithdrawnEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 회원 탈퇴 후 로그인 세션 삭제
 * 탈퇴가 롤백되면 세션을 지우지 않도록 커밋 후 실행
 * 삭제에 실패해도 Access Token 인증과 토큰 재발급이 비활성 사용자를 막고, 재발급 시 남은 세션도 지우므로 로그만 남김
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserWithdrawnEventListener {

    private final RefreshTokenStore refreshTokenStore;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UserWithdrawnEvent event) {
        try {
            refreshTokenStore.deleteAllByUserId(event.userId());
        } catch (RuntimeException exception) {
            log.error("탈퇴 사용자의 로그인 세션 삭제 실패: userId={}", event.userId(), exception);
        }
    }
}
