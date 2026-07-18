package com.ebook.bookforest.domain.user.event;

import com.ebook.bookforest.domain.user.service.SocialUnlinkTaskProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
