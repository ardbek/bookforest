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

/**
 * 소셜 연동 해제 작업 주기 처리 스케줄러
 * 탈퇴 직후 이벤트 처리에서 실패했거나 누락된 작업을 다시 처리
 */
@Component
@RequiredArgsConstructor
public class SocialUnlinkRetryScheduler {

    /** 처리 중 상태가 이 시간보다 오래 지속되면 서버가 멈춘 것으로 판단 */
    private static final Duration PROCESSING_TIMEOUT =
        Duration.ofMinutes(10);

    private static final Set<SocialUnlinkStatus> READY_STATUSES = Set.of(
        SocialUnlinkStatus.PENDING,
        SocialUnlinkStatus.RETRY_WAITING
    );

    private final SocialUnlinkTaskRepository taskRepository;
    private final SocialUnlinkTaskStateService stateService;
    private final SocialUnlinkTaskProcessor processor;

    /** 멈춘 작업 복구 후 처리할 시점이 된 작업 실행(각각 최대 100건) */
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
