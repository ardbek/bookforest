package com.ebook.bookforest.domain.book.repository;

public record BookSubscriptionAsset(
    Long bookOriginId,
    String bookUrl,
    String coverImgUrl
) {
}
