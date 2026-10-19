package com.ebook.bookforest.domain.category.entity;

import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플랫폼마다 다른 카테고리를 통합하는 서비스 공통 카테고리
 * 시드 SQL이 등록하고 이 애플리케이션은 조회만 담당
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    private static final int DEFAULT_DEPTH = 1;
    private static final int DEFAULT_SORT_ORDER = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "standard_category_id")
    private Long id;

    @Column(name = "category_code", nullable = false, length = 100)
    private String categoryCode;

    @Column(name = "category_name", nullable = false, length = 200)
    private String categoryName;

    /** 부모 표준 카테고리, 최상위 카테고리는 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    private StandardCategory parentCategory;

    /** 조상 포함 경로, 예: 소설>한국소설 */
    @Column(name = "category_path", length = 500)
    private String categoryPath;

    /**
     * 자동 매핑용 별칭, 콤마 구분
     * 소문자 변환 후 공백, '/', '·', '.', '-'를 제거한 정규화 형태
     */
    @Column(name = "aliases", length = 1000)
    private String aliases;

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
    private StandardCategory(
        String categoryCode,
        String categoryName,
        StandardCategory parentCategory,
        String categoryPath,
        String aliases,
        Integer depth,
        Integer sortOrder
    ) {
        this.categoryCode = Objects.requireNonNull(categoryCode, "카테고리 코드는 필수입니다.");
        this.categoryName = Objects.requireNonNull(categoryName, "카테고리명은 필수입니다.");
        this.parentCategory = parentCategory;
        this.categoryPath = categoryPath;
        this.aliases = aliases;
        this.depth = depth == null ? DEFAULT_DEPTH : depth;
        this.sortOrder = sortOrder == null ? DEFAULT_SORT_ORDER : sortOrder;
    }
}
