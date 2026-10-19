package com.ebook.bookforest.domain.book.dto;

import com.ebook.bookforest.domain.book.repository.BookSubscriptionPlatformLink;

/**
 * 도서를 제공하는 구독 플랫폼 이동 링크
 */
public record BookSubscriptionPlatformResponse(
    Long platformId,
    String platformCode,
    String platformName,
    String bookUrl
) {

    public static BookSubscriptionPlatformResponse from(BookSubscriptionPlatformLink link) {
        return new BookSubscriptionPlatformResponse(
            link.platformId(),
            link.platformCode(),
            link.platformName(),
            link.bookUrl()
        );
    }
}
