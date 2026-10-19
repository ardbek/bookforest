package com.ebook.bookforest.domain.book.repository;

/**
 * 원본 도서를 제공하는 플랫폼별 건수 조회 결과
 */
public record BookPlatformSummary(
    Long bookOriginId,
    Long platformId,
    String platformCode,
    String platformName,
    long itemCount
) {
}
