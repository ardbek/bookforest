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
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 탈퇴 후 처리할 소셜 연동 해제 작업
 * 외부 API 실패에 대비해 상태와 재시도 정보를 DB에 기록
 *
 * <p>상태 전이
 * <ul>
 *   <li>PENDING, RETRY_WAITING: PROCESSING으로 선점</li>
 *   <li>PROCESSING: SUCCEEDED, RETRY_WAITING, FAILED 중 하나로 종료</li>
 * </ul>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider", nullable = false, length = 30)
    private SocialProvider socialProvider;

    /** 소셜 플랫폼이 부여한 사용자 식별값 */
    @Column(name = "provider_subject", nullable = false, length = 200)
    private String providerSubject;

    /** 네이버 연동 해제용 암호화 토큰, 카카오는 Admin Key를 써서 null, 성공하면 삭제 */
    @Column(name = "provider_token_ciphertext", length = 4096)
    private String providerTokenCiphertext;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_token_type", length = 30)
    private ProviderTokenType providerTokenType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SocialUnlinkStatus status = SocialUnlinkStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    /** 처리 가능해지는 시각, 처리 중이거나 종료된 작업은 null */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /** 마지막 실패 원인 코드, SocialUnlinkClientException 코드나 처리기 내부 코드 */
    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 여러 서버가 같은 작업을 동시에 수정하지 못하게 하는 낙관적 락 버전 */
    @Version
    @Column(name = "version")
    private Long version;

    private SocialUnlinkTask(
        SocialProvider socialProvider,
        String providerSubject,
        String providerTokenCiphertext,
        ProviderTokenType providerTokenType,
        LocalDateTime nextRetryAt
    ) {
        this.socialProvider = socialProvider;
        this.providerSubject = providerSubject;
        this.providerTokenCiphertext = providerTokenCiphertext;
        this.providerTokenType = providerTokenType;
        this.nextRetryAt = nextRetryAt;
    }

    /** 연동 해제 작업 생성(대기 상태, 즉시 처리 가능) */
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

        return new SocialUnlinkTask(provider, providerSubject, providerTokenCiphertext, providerTokenType, now);
    }

    /** 처리 가능 여부(대기 상태, 처리 시각 도래) */
    public boolean isReady(LocalDateTime now) {
        Objects.requireNonNull(now, "현재 시각은 필수입니다.");

        boolean readyStatus =
            status == SocialUnlinkStatus.PENDING
                || status == SocialUnlinkStatus.RETRY_WAITING;

        return readyStatus
            && nextRetryAt != null
            && !nextRetryAt.isAfter(now);
    }

    /** 처리 시작(선점) */
    public void startProcessing() {
        requireStatus(
            SocialUnlinkStatus.PENDING,
            SocialUnlinkStatus.RETRY_WAITING
        );

        this.status = SocialUnlinkStatus.PROCESSING;
        this.nextRetryAt = null;
    }

    /** 연동 해제 성공 처리, 더 쓸 일이 없는 토큰 암호문 삭제 */
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

    /** 재시도 예약(재시도 대기 상태) */
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

    /** 최종 실패 처리(재시도 소진 또는 복구 불가) */
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
