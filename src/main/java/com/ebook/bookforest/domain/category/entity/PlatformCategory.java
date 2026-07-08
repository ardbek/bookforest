package com.ebook.bookforest.domain.category.entity;

import com.ebook.bookforest.domain.category.enums.MappingStatus;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
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

    /* 플랫폼 카테고리 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "platform_category_id")
    private Long id;

    /* 플랫폼 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    /* 플랫폼 카테고리 코드 */
    @Column(name = "category_code", nullable = false, length = 100)
    private String categoryCode;

    /* 플랫폼 카테고리명 */
    @Column(name = "category_name", nullable = false, length = 200)
    private String categoryName;

    /*
    * 부모 플랫폼 카테고리
    *
    * 교보 무료도서관은 부모 코드의 이름이 도서관마다 달라 모호하므로 null로 운용.
    * 계층은 표준 카테고리가 담당한다.
    */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_platform_category_id")
    private PlatformCategory parentPlatformCategory;

    /* 매핑된 표준 카테고리 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "standard_category_id")
    private StandardCategory standardCategory;

    /* 카테고리 경로 */
    @Column(name = "category_path", length = 1000)
    private String categoryPath;

    /* 매핑 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_status", nullable = false, length = 20)
    @Builder.Default
    private MappingStatus mappingStatus = MappingStatus.UNMAPPED;

    /* 자동 매핑 신뢰도 (0.00 ~ 1.00) */
    @Column(name = "mapping_confidence", precision = 3, scale = 2)
    private BigDecimal mappingConfidence;

    /* 매핑 확정자 */
    @Column(name = "mapped_by", length = 100)
    private String mappedBy;

    /* 매핑 확정 시각 */
    @Column(name = "mapped_at")
    private LocalDateTime mappedAt;

    /* 깊이 */
    @Column(name = "depth", nullable = false)
    @Builder.Default
    private int depth = 1;

    /* 정렬 순서 */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    /* 활성화 여부 */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /* 자동 매핑 배치가 후보를 지정 */
    public void applyAutoMapping(
        StandardCategory standardCategory,
        BigDecimal confidence
    ) {
        this.standardCategory = standardCategory;
        this.mappingConfidence = confidence;
        this.mappingStatus = MappingStatus.AUTO_MAPPED;
    }

    /* 관리자가 매핑 확정 */
    public void confirmMapping(
        StandardCategory standardCategory,
        String adminName,
        LocalDateTime confirmedAt
    ) {
        this.standardCategory = standardCategory;
        this.mappingStatus = MappingStatus.CONFIRMED;
        this.mappedBy = adminName;
        this.mappedAt = confirmedAt;
    }

    /* 매핑 제외 처리 */
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