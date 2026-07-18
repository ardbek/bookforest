package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.user.entity.SocialUnlinkTask;
import com.ebook.bookforest.domain.user.enums.SocialUnlinkStatus;
import com.ebook.bookforest.domain.user.repository.SocialUnlinkTaskRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SocialUnlinkRetryScheduler {

    private static final Duration PROCESSING_TIMEOUT =
        Duration.ofMinutes(10);

    private static final Set<SocialUnlinkStatus> READY_STATUSES = Set.of(
        SocialUnlinkStatus.PENDING,
        SocialUnlinkStatus.RETRY_WAITING
    );

    private final SocialUnlinkTaskRepository taskRepository;
    private final SocialUnlinkTaskStateService stateService;
    private final SocialUnlinkTaskProcessor processor;

    @Scheduled(
        fixedDelayString =
            "${app.security.social-unlink.scheduler-delay-ms:30000}"
    )
    public void processTasks() {
        LocalDateTime now = LocalDateTime.now();

        taskRepository
            .findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                SocialUnlinkStatus.PROCESSING,
                now.minus(PROCESSING_TIMEOUT)
            )
            .stream()
            .map(SocialUnlinkTask::getId)
            .forEach(taskId ->
                stateService.recoverStaleProcessing(taskId, now)
            );

        taskRepository
            .findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                READY_STATUSES,
                now
            )
            .stream()
            .map(SocialUnlinkTask::getId)
            .forEach(processor::process);
    }
}
