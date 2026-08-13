package com.ebook.bookforest.domain.book.repository;

public record BookSubscriptionPlatformLink(
    Long bookOriginId,
    Long platformId,
    String platformCode,
    String platformName,
    String bookUrl
) {
}
