package com.ebook.bookforest.domain.book.repository;

public record BookPlatformSummary(
    Long bookId,
    Long platformId,
    String platformCode,
    String platformName,
    long itemCount
) {
}
