package com.ebook.bookforest.domain.category.entity;

import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "platform_category")
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
    @Column(name = "category_code", nullable = false, length = 200)
    private String categoryCode;

    /* 플랫폼 카테고리명 */
    @Column(name = "category_name", nullable = false, length = 300)
    private String categoryName;

    /* 부모 플랫폼 카테고리 */
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

    public void mapToStandardCategory(StandardCategory standardCategory) {
        this.standardCategory = standardCategory;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}