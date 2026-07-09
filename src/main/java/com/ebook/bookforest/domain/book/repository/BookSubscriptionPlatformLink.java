package com.ebook.bookforest.domain.book.repository;

public record BookSubscriptionPlatformLink(
    Long bookId,
    Long platformId,
    String platformCode,
    String platformName,
    String bookUrl
) {
}
