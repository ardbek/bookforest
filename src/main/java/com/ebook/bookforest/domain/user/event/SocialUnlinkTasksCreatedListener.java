package com.ebook.bookforest.domain.user.event;

import com.ebook.bookforest.domain.user.service.SocialUnlinkTaskProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 연동 해제 작업 생성 이벤트 처리기
 * 탈퇴 응답을 늦추지 않도록 커밋 후 별도 스레드에서 실행, 실패한 작업은 재시도 스케줄러가 다시 처리
 */
@Component
@RequiredArgsConstructor
public class SocialUnlinkTasksCreatedListener {

    private final SocialUnlinkTaskProcessor processor;

    @Async("socialUnlinkExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SocialUnlinkTasksCreatedEvent event) {
        event.taskIds().forEach(processor::process);
    }
}
