package com.ebook.bookforest.domain.book.repository;

public record BookCategorySummary(
    Long bookId,
    Long categoryId,
    String categoryName,
    String categoryPath
) {
}
