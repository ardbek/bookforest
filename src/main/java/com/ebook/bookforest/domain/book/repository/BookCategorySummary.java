package com.ebook.bookforest.domain.book.repository;

public record BookCategorySummary(
    Long bookOriginId,
    Long categoryId,
    String categoryName,
    String categoryPath
) {
}
