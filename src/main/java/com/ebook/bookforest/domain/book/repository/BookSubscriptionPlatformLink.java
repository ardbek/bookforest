package com.ebook.bookforest.domain.book.repository;

/**
 * 구독 플랫폼 이동 링크 조회 결과
 */
public record BookSubscriptionPlatformLink(
    Long bookOriginId,
    Long platformId,
    String platformCode,
    String platformName,
    String bookUrl
) {
}
