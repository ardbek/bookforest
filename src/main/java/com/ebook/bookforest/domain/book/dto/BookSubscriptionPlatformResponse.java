package com.ebook.bookforest.domain.book.dto;

import com.ebook.bookforest.domain.book.repository.BookSubscriptionPlatformLink;

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
