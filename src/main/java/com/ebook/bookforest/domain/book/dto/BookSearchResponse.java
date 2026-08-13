package com.ebook.bookforest.domain.book.dto;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.repository.BookCategorySummary;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public record BookSearchResponse(
    Long bookOriginId,
    Long platformId,
    String platformCode,
    String platformName,
    String title,
    String author,
    String publisher,
    LocalDate publishedDate,
    String isbn13,
    String isbn10,
    String coverImgUrl,
    String description,
    Long primaryCategoryId,
    String primaryCategoryName,
    String primaryCategoryPath,
    long libraryCount,
    long libraryPlatformCount,
    long subscriptionPlatformCount,
    List<BookPlatformSummaryResponse> libraryPlatforms,
    List<BookPlatformSummaryResponse> subscriptionPlatforms,
    List<BookSubscriptionPlatformResponse> subscriptionPlatformLinks,
    List<BookLibraryPlatformHoldingResponse> libraryPlatformHoldings,
    boolean favoriteYn
) {

    public static BookSearchResponse from(
        BookOrigin bookOrigin,
        BookCategorySummary category,
        List<BookPlatformSummaryResponse> libraryPlatforms,
        List<BookPlatformSummaryResponse> subscriptionPlatforms,
        boolean favoriteYn
    ) {
        return from(
            bookOrigin,
            category,
            libraryPlatforms,
            subscriptionPlatforms,
            Collections.emptyList(),
            Collections.emptyList(),
            favoriteYn
        );
    }

    public static BookSearchResponse from(
        BookOrigin bookOrigin,
        BookCategorySummary category,
        List<BookPlatformSummaryResponse> libraryPlatforms,
        List<BookPlatformSummaryResponse> subscriptionPlatforms,
        List<BookSubscriptionPlatformResponse> subscriptionPlatformLinks,
        List<BookLibraryPlatformHoldingResponse> libraryPlatformHoldings,
        boolean favoriteYn
    ) {
        long libraryCount = libraryPlatforms.stream()
            .mapToLong(BookPlatformSummaryResponse::itemCount)
            .sum();

        return new BookSearchResponse(
            bookOrigin.getId(),
            bookOrigin.getPlatform().getId(),
            bookOrigin.getPlatform().getPlatformCode(),
            bookOrigin.getPlatform().getPlatformName(),
            bookOrigin.getTitleRaw(),
            bookOrigin.getAuthorRaw(),
            bookOrigin.getPublisherRaw(),
            bookOrigin.getPublishedDate(),
            bookOrigin.getIsbn13Raw(),
            bookOrigin.getIsbn10Raw(),
            bookOrigin.getCoverImgUrl(),
            bookOrigin.getDescriptionRaw(),
            category != null ? category.categoryId() : null,
            category != null ? category.categoryName() : null,
            category != null ? category.categoryPath() : null,
            libraryCount,
            libraryPlatforms.size(),
            subscriptionPlatforms.size(),
            libraryPlatforms,
            subscriptionPlatforms,
            subscriptionPlatformLinks,
            libraryPlatformHoldings,
            favoriteYn
        );
    }
}
