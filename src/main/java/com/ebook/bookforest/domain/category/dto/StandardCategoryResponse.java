package com.ebook.bookforest.domain.category.dto;

import com.ebook.bookforest.domain.category.entity.StandardCategory;

public record StandardCategoryResponse(
    Long standardCategoryId,
    String categoryCode,
    String categoryName,
    String categoryPath,
    Long parentCategoryId,
    int depth,
    int sortOrder
) {

    public static StandardCategoryResponse from(StandardCategory category) {
        return new StandardCategoryResponse(
            category.getId(),
            category.getCategoryCode(),
            category.getCategoryName(),
            category.getCategoryPath(),
            category.getParentCategory() != null ? category.getParentCategory().getId() : null,
            category.getDepth(),
            category.getSortOrder()
        );
    }
}
