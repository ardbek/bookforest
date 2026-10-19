package com.ebook.bookforest.domain.book.dto;

/**
 * 도서를 제공하는 플랫폼 요약
 */
public record BookPlatformSummaryResponse(
    Long platformId,
    String platformCode,
    String platformName,
    /** 도서관 플랫폼은 보유 도서관 수, 구독 플랫폼은 제공 건수 */
    long itemCount
) {
}
