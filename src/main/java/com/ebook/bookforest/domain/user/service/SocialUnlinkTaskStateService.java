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

/**
 * 소셜 연동 해제 작업 상태 변경 서비스
 * 외부 API 호출 앞뒤의 상태 기록을 각각 독립된 짧은 트랜잭션으로 처리
 * 여러 서버가 같은 작업을 동시에 바꾸지 않도록 비관적 락으로 조회
 */
@Service
@RequiredArgsConstructor
public class SocialUnlinkTaskStateService {

    private static final int MAX_RETRY_COUNT = 5;
    /** 재시도 간격 기본값, 재시도할 때마다 두 배로 늘리되 최대 간격을 넘지 않음 */
    private static final Duration BASE_RETRY_DELAY = Duration.ofMinutes(1);
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);

    private final SocialUnlinkTaskRepository taskRepository;

    /**
     * 처리할 작업 선점(처리 중 상태로 전환)
     *
     * @return 처리할 시점이 아니거나 이미 선점된 작업이면 빈 Optional
     */
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

    /** 작업 성공 처리 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(Long taskId, LocalDateTime now) {
        findForUpdate(taskId).markSucceeded(now);
    }

    /** 작업 실패 처리, 재시도 가능하고 횟수가 남았으면 재시도 예약, 아니면 최종 실패 */
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

    /**
     * 처리 중에 서버가 멈춰 남은 작업 복구(재시도 가능한 실패로 처리)
     *
     * @return 처리 중 상태였던 작업을 복구했으면 true
     */
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
