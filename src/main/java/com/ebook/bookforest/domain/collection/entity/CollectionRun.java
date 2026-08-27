package com.ebook.bookforest.domain.collection.entity;

import com.ebook.bookforest.domain.collection.enums.CollectionStatus;
import com.ebook.bookforest.domain.collection.enums.CollectionTargetType;
import com.ebook.bookforest.domain.library.entity.Library;
import com.ebook.bookforest.domain.platform.entity.Platform;
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
    name = "collection_run",
    indexes = {
        @Index(
            name = "idx_collection_run_platform_started",
            columnList = "platform_id, started_at"
        )
    }
)
public class CollectionRun extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "collection_run_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id")
    private Library library;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 50)
    private CollectionTargetType targetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CollectionStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "total_count", nullable = false)
    @Builder.Default
    private int totalCount = 0;

    @Column(name = "success_count", nullable = false)
    @Builder.Default
    private int successCount = 0;

    @Column(name = "fail_count", nullable = false)
    @Builder.Default
    private int failCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public void success(
        int totalCount,
        int successCount,
        int failCount
    ) {
        this.status = CollectionStatus.SUCCESS;
        this.finishedAt = LocalDateTime.now();
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failCount = failCount;
    }

    public void fail(String errorMessage) {
        this.status = CollectionStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }
}