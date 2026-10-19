package com.ebook.bookforest.domain.category.entity;

import com.ebook.bookforest.domain.category.enums.MappingStatus;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플랫폼이 원본 데이터에서 쓰는 카테고리와 표준 카테고리 매핑
 * 같은 코드라도 도서관마다 의미가 달라 플랫폼, 코드, 이름 세 값으로 식별
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "platform_category",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_platform_category_identity",
            columnNames = {"platform_id", "category_code", "category_name"}
        )
    },
    indexes = {
        @Index(
            name = "idx_pc_standard",
            columnList = "standard_category_id"
        ),
        @Index(
            name = "idx_pc_mapping_status",
            columnList = "mapping_status, platform_id"
        )
    }
)
public class PlatformCategory extends BaseTimeEntity {

    private static final int DEFAULT_DEPTH = 1;
    private static final int DEFAULT_SORT_ORDER = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "platform_category_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    /** 플랫폼이 부여한 카테고리 코드, 도서관별로 의미가 다를 수 있는 값 */
    @Column(name = "category_code", nullable = false, length = 100)
    private String categoryCode;

    @Column(name = "category_name", nullable = false, length = 200)
    private String categoryName;

    /**
     * 부모 플랫폼 카테고리
     * 교보 무료도서관은 부모 코드의 이름이 도서관마다 달라 null로 운용, 계층은 표준 카테고리가 담당
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_platform_category_id")
    private PlatformCategory parentPlatformCategory;

    /** 매핑된 표준 카테고리, 미매핑이나 제외 상태에서는 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "standard_category_id")
    private StandardCategory standardCategory;

    /** 플랫폼 원본 카테고리 경로 */
    @Column(name = "category_path", length = 1000)
    private String categoryPath;

    /** 표준 카테고리 매핑 진행 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_status", nullable = false, length = 20)
    private MappingStatus mappingStatus = MappingStatus.UNMAPPED;

    /** 자동 매핑 신뢰도, 0.00 ~ 1.00 */
    @Column(name = "mapping_confidence", precision = 3, scale = 2)
    private BigDecimal mappingConfidence;

    /** 매핑을 확정하거나 제외한 관리자 */
    @Column(name = "mapped_by", length = 100)
    private String mappedBy;

    /** 매핑 확정 또는 제외 시각 */
    @Column(name = "mapped_at")
    private LocalDateTime mappedAt;

    /** 계층 깊이, 최상위 1 */
    @Column(name = "depth", nullable = false)
    private int depth;

    /** 같은 깊이 안의 정렬 순서 */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 서비스 사용 여부 */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** 깊이와 정렬 순서 미지정 시 기본값 1, 0 */
    @Builder
    private PlatformCategory(
        Platform platform,
        String categoryCode,
        String categoryName,
        PlatformCategory parentPlatformCategory,
        String categoryPath,
        Integer depth,
        Integer sortOrder
    ) {
        this.platform = Objects.requireNonNull(platform, "플랫폼은 필수입니다.");
        this.categoryCode = Objects.requireNonNull(categoryCode, "카테고리 코드는 필수입니다.");
        this.categoryName = Objects.requireNonNull(categoryName, "카테고리명은 필수입니다.");
        this.parentPlatformCategory = parentPlatformCategory;
        this.categoryPath = categoryPath;
        this.depth = depth == null ? DEFAULT_DEPTH : depth;
        this.sortOrder = sortOrder == null ? DEFAULT_SORT_ORDER : sortOrder;
    }

    /** 자동 매핑 배치의 표준 카테고리 후보 지정, 관리자 검토 대기 상태로 전환 */
    public void applyAutoMapping(StandardCategory standardCategory, BigDecimal confidence) {
        this.standardCategory = standardCategory;
        this.mappingConfidence = confidence;
        this.mappingStatus = MappingStatus.AUTO_MAPPED;
    }

    /** 관리자의 표준 카테고리 매핑 확정, 자동 매핑 신뢰도는 유지 */
    public void confirmMapping(StandardCategory standardCategory, String adminName, LocalDateTime confirmedAt) {
        this.standardCategory = standardCategory;
        this.mappingStatus = MappingStatus.CONFIRMED;
        this.mappedBy = adminName;
        this.mappedAt = confirmedAt;
    }

    /** 매핑 대상 제외, 지정된 표준 카테고리와 신뢰도 초기화 */
    public void ignoreMapping(String adminName, LocalDateTime ignoredAt) {
        this.standardCategory = null;
        this.mappingConfidence = null;
        this.mappingStatus = MappingStatus.IGNORED;
        this.mappedBy = adminName;
        this.mappedAt = ignoredAt;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
