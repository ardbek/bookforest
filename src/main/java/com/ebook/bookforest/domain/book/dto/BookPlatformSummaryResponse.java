package com.ebook.bookforest.domain.book.dto;

public record BookPlatformSummaryResponse(
    Long platformId,
    String platformCode,
    String platformName,
    long itemCount
) {
}
