package com.ebook.bookforest.domain.book.repository;

/**
 * 원본 도서의 표준 카테고리 조회 결과
 */
public record BookCategorySummary(
    Long bookOriginId,
    Long categoryId,
    String categoryName,
    String categoryPath
) {
}
