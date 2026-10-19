package com.ebook.bookforest.domain.book.entity;

import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구독 플랫폼의 원본 도서 제공 정보, 원본 도서당 한 건
 * 수집기가 등록하고 갱신, 이 애플리케이션은 조회만 담당
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "subscription_book",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_subscription_book_origin",
        columnNames = "book_origin_id"
    )
)
public class SubscriptionBook extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_book_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_origin_id", nullable = false)
    private BookOrigin bookOrigin;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 30)
    private AvailabilityStatus availabilityStatus;

    /** 구독 플랫폼 도서 상세 페이지 URL, 없으면 원본 도서 URL 사용 */
    @Column(name = "book_url", length = 1000)
    private String bookUrl;

    /** 구독 플랫폼 표지 URL, 없으면 원본 도서 표지 사용 */
    @Column(name = "cover_img_url", length = 1000)
    private String coverImgUrl;

    @Column(name = "first_seen_at")
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    /** 마지막으로 발견한 수집 실행 ID */
    @Column(name = "last_seen_run_id")
    private Long lastSeenRunId;

    /** 구독 플랫폼에서 사라진 것을 확인한 시각 */
    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    /** 수집 시점 이용 상태 미지정 시 기본값 AVAILABLE */
    @Builder
    private SubscriptionBook(
        BookOrigin bookOrigin,
        AvailabilityStatus availabilityStatus,
        String bookUrl,
        String coverImgUrl
    ) {
        this.bookOrigin = Objects.requireNonNull(bookOrigin, "원본 도서는 필수입니다.");
        this.availabilityStatus = availabilityStatus == null ? AvailabilityStatus.AVAILABLE : availabilityStatus;
        this.bookUrl = bookUrl;
        this.coverImgUrl = coverImgUrl;
    }

    /** 수집에서 다시 발견한 제공 정보의 이용 가능 처리 */
    public void markAvailable(LocalDateTime seenAt, Long collectionRunId) {
        this.availabilityStatus = AvailabilityStatus.AVAILABLE;
        this.lastSeenAt = seenAt;
        this.lastSeenRunId = collectionRunId;
        this.removedAt = null;

        if (this.firstSeenAt == null) {
            this.firstSeenAt = seenAt;
        }
    }

    /** 수집에서 사라진 제공 정보의 제거 처리 */
    public void markRemoved(LocalDateTime removedAt) {
        this.availabilityStatus = AvailabilityStatus.REMOVED;
        this.removedAt = removedAt;
    }
}
