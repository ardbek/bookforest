package com.ebook.bookforest.domain.category.dto;

import com.ebook.bookforest.domain.category.entity.StandardCategory;

/**
 * 표준 카테고리 목록 응답 항목
 */
public record StandardCategoryResponse(
    Long standardCategoryId,
    String categoryCode,
    String categoryName,
    /** 조상 포함 경로, 예: 소설>한국소설 */
    String categoryPath,
    /** 부모 표준 카테고리 ID, 최상위 카테고리는 null */
    Long parentCategoryId,
    int depth,
    int sortOrder
) {

    public static StandardCategoryResponse from(StandardCategory category) {
        StandardCategory parentCategory = category.getParentCategory();

        return new StandardCategoryResponse(
            category.getId(),
            category.getCategoryCode(),
            category.getCategoryName(),
            category.getCategoryPath(),
            parentCategory == null ? null : parentCategory.getId(),
            category.getDepth(),
            category.getSortOrder()
        );
    }
}
