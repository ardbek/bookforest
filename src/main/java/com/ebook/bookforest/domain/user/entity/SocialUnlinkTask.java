package com.ebook.bookforest.domain.user.entity;

import com.ebook.bookforest.domain.user.enums.ProviderTokenType;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.enums.SocialUnlinkStatus;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(
    name = "social_unlink_task",
    indexes = {
        @Index(
            name = "idx_social_unlink_task_retry",
            columnList = "status, next_retry_at"
        ),
        @Index(
            name = "idx_social_unlink_task_provider_subject",
            columnList = "social_provider, provider_subject, status"
        ),
        @Index(
            name = "idx_social_unlink_task_processing_timeout",
            columnList = "status, updated_at"
        )
    }
)
public class SocialUnlinkTask extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "social_unlink_task_id")
    private Long id;

    /**
     * 연동을 해제할 소셜 플랫폼
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider", nullable = false, length = 30)
    private SocialProvider socialProvider;

    /**
     * 소셜 플랫폼 사용자 식별값
     */
    @Column(name = "provider_subject", nullable = false, length = 200)
    private String providerSubject;

    /**
     * 네이버 연동 해제에 사용할 암호화된 토큰 (카카오는 admin key를 사용)
     */
    @Column(name = "provider_token_ciphertext", length = 4096)
    private String providerTokenCiphertext;

    /**
     * Access/Refresh 구분 값
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider_token_type", length = 30)
    private ProviderTokenType providerTokenType;

    /**
     * 연동 해제 작업 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private SocialUnlinkStatus status = SocialUnlinkStatus.PENDING;

    /**
     * 재시도 횟수
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /**
     * 다음 재시도 가능 시각
     */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /**
     * 마지막 실패 원인 코드
     */
    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    /**
     * 연동 해제 완료 시각
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * 여러 서버가 같은 작업을 동시에 수정하는 것을 방지
     */
    @Version
    @Column(name = "version")
    private Long version;

    public static SocialUnlinkTask createPending(
        SocialProvider provider,
        String providerSubject,
        String providerTokenCiphertext,
        ProviderTokenType providerTokenType,
        LocalDateTime now
    ) {
        Objects.requireNonNull(provider, "소셜 플랫폼은 필수입니다.");
        Objects.requireNonNull(now, "작업 생성 시각은 필수입니다.");

        if (providerSubject == null || providerSubject.isBlank()) {
            throw new IllegalArgumentException("소셜 사용자 식별값은 필수입니다.");
        }

        return SocialUnlinkTask.builder()
            .socialProvider(provider)
            .providerSubject(providerSubject)
            .providerTokenCiphertext(providerTokenCiphertext)
            .providerTokenType(providerTokenType)
            .status(SocialUnlinkStatus.PENDING)
            .retryCount(0)
            .nextRetryAt(now)
            .build();
    }

    public boolean isReady(LocalDateTime now) {
        Objects.requireNonNull(now, "현재 시각은 필수입니다.");

        boolean readyStatus =
            status == SocialUnlinkStatus.PENDING
                || status == SocialUnlinkStatus.RETRY_WAITING;

        return readyStatus
            && nextRetryAt != null
            && !nextRetryAt.isAfter(now);
    }

    public void startProcessing() {
        requireStatus(
            SocialUnlinkStatus.PENDING,
            SocialUnlinkStatus.RETRY_WAITING
        );

        this.status = SocialUnlinkStatus.PROCESSING;
        this.nextRetryAt = null;
    }

    public void markSucceeded(LocalDateTime completedAt) {
        requireStatus(SocialUnlinkStatus.PROCESSING);

        LocalDateTime validatedCompletedAt = Objects.requireNonNull(completedAt, "완료 시각은 필수입니다.");

        this.status = SocialUnlinkStatus.SUCCEEDED;
        this.completedAt = validatedCompletedAt;
        this.nextRetryAt = null;
        this.lastErrorCode = null;
        this.providerTokenCiphertext = null;
        this.providerTokenType = null;
    }

    public void scheduleRetry(
        String errorCode,
        LocalDateTime nextRetryAt
    ) {
        requireStatus(SocialUnlinkStatus.PROCESSING);

        String validatedErrorCode = requireErrorCode(errorCode);
        LocalDateTime validatedNextRetryAt = Objects.requireNonNull(nextRetryAt, "다음 재시도 시각은 필수입니다.");

        this.status = SocialUnlinkStatus.RETRY_WAITING;
        this.retryCount++;
        this.lastErrorCode = validatedErrorCode;
        this.nextRetryAt = validatedNextRetryAt;
    }

    public void markFailed(String errorCode) {
        requireStatus(SocialUnlinkStatus.PROCESSING);

        String validatedErrorCode = requireErrorCode(errorCode);

        this.status = SocialUnlinkStatus.FAILED;
        this.lastErrorCode = validatedErrorCode;
        this.nextRetryAt = null;
    }

    private String requireErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("오류 코드는 필수입니다.");
        }

        return errorCode;
    }

    private void requireStatus(SocialUnlinkStatus... allowedStatuses) {
        for (SocialUnlinkStatus allowedStatus : allowedStatuses) {
            if (this.status == allowedStatus) {
                return;
            }
        }

        throw new IllegalStateException("현재 상태에서는 연동 해제 작업을 변경할 수 없습니다. status=" + status);
    }

}
