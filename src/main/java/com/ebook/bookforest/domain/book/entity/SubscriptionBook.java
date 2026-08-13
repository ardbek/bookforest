package com.ebook.bookforest.domain.book.entity;

import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(
    name = "subscription_book",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_subscription_book_origin",
        columnNames = "book_origin_id"
    )
)
public class SubscriptionBook extends BaseTimeEntity {

    /* 구독 플랫폼 보유 도서 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscription_book_id")
    private Long id;

    /* 플랫폼 도서 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "book_origin_id",
        nullable = false
    )
    private BookOrigin bookOrigin;

    /* 제공 상태 */
    @Enumerated(EnumType.STRING)
    @Column(
        name = "availability_status",
        nullable = false,
        length = 30
    )
    @Builder.Default
    private AvailabilityStatus availabilityStatus = AvailabilityStatus.AVAILABLE;

    /* 구독 플랫폼 도서 접근 URL */
    @Column(
        name = "book_url",
        length = 1000
    )
    private String bookUrl;

    /* 표지 이미지 URL */
    @Column(
        name = "cover_img_url",
        length = 1000
    )
    private String coverImgUrl;

    /* 최초 발견 시각 */
    @Column(name = "first_seen_at")
    private LocalDateTime firstSeenAt;

    /* 마지막 발견 시각 */
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    /* 마지막 수집 run id */
    @Column(name = "last_seen_run_id")
    private Long lastSeenRunId;

    /* 제거 확인 시각 */
    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    public void markAvailable(
        LocalDateTime seenAt,
        Long collectionRunId
    ) {
        this.availabilityStatus = AvailabilityStatus.AVAILABLE;
        this.lastSeenAt = seenAt;
        this.lastSeenRunId = collectionRunId;
        this.removedAt = null;

        if (this.firstSeenAt == null) {
            this.firstSeenAt = seenAt;
        }
    }

    public void markRemoved(LocalDateTime removedAt) {
        this.availabilityStatus = AvailabilityStatus.REMOVED;
        this.removedAt = removedAt;
    }

}
