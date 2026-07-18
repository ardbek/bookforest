package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.user.entity.SocialUnlinkTask;
import com.ebook.bookforest.domain.user.enums.SocialUnlinkStatus;
import com.ebook.bookforest.domain.user.repository.SocialUnlinkTaskRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialUnlinkTaskStateService {

    private static final int MAX_RETRY_COUNT = 5;
    private static final Duration BASE_RETRY_DELAY = Duration.ofMinutes(1);
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);

    private final SocialUnlinkTaskRepository taskRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SocialUnlinkWork> claim(
        Long taskId,
        LocalDateTime now
    ) {
        SocialUnlinkTask task = findForUpdate(taskId);

        if (!task.isReady(now)) {
            return Optional.empty();
        }

        task.startProcessing();

        return Optional.of(new SocialUnlinkWork(
            task.getId(),
            task.getSocialProvider(),
            task.getProviderSubject(),
            task.getProviderTokenCiphertext(),
            task.getProviderTokenType()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(Long taskId, LocalDateTime now) {
        findForUpdate(taskId).markSucceeded(now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailure(
        Long taskId,
        boolean retryable,
        String errorCode,
        LocalDateTime now
    ) {
        SocialUnlinkTask task = findForUpdate(taskId);

        applyFailure(task, retryable, errorCode, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recoverStaleProcessing(
        Long taskId,
        LocalDateTime now
    ) {
        SocialUnlinkTask task = findForUpdate(taskId);

        if (task.getStatus() != SocialUnlinkStatus.PROCESSING) {
            return false;
        }

        applyFailure(task, true, "PROCESSING_TIMEOUT", now);
        return true;
    }

    private void applyFailure(
        SocialUnlinkTask task,
        boolean retryable,
        String errorCode,
        LocalDateTime now
    ) {

        if (retryable && task.getRetryCount() < MAX_RETRY_COUNT) {
            task.scheduleRetry(
                errorCode,
                now.plus(calculateDelay(task.getRetryCount()))
            );
            return;
        }

        task.markFailed(errorCode);
    }

    private SocialUnlinkTask findForUpdate(Long taskId) {
        return taskRepository.findByIdForUpdate(taskId)
            .orElseThrow(() ->
                new IllegalArgumentException("연동 해제 작업을 찾을 수 없습니다.")
            );
    }

    private Duration calculateDelay(int retryCount) {
        long multiplier = 1L << Math.min(retryCount, 6);
        Duration delay = BASE_RETRY_DELAY.multipliedBy(multiplier);

        return delay.compareTo(MAX_RETRY_DELAY) > 0
            ? MAX_RETRY_DELAY
            : delay;
    }
}
