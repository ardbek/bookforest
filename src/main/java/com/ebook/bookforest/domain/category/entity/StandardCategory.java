package com.ebook.bookforest.domain.category.entity;

import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(
    name = "standard_category",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_standard_category_code",
            columnNames = "category_code"
        )
    }
)
public class StandardCategory extends BaseTimeEntity {

    /* 표준 카테고리 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "standard_category_id")
    private Long id;

    /* 카테고리 코드 */
    @Column(name = "category_code", nullable = false, length = 100)
    private String categoryCode;

    /* 카테고리명 */
    @Column(name = "category_name", nullable = false, length = 200)
    private String categoryName;

    /* 부모 카테고리 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    private StandardCategory parentCategory;

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

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}