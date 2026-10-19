package com.ebook.bookforest.domain.book.repository;

/**
 * 구독 도서의 이동 링크와 표지 조회 결과, 구독 값이 없으면 원본 도서 값
 */
public record BookSubscriptionAsset(
    Long bookOriginId,
    String bookUrl,
    String coverImgUrl
) {
}
