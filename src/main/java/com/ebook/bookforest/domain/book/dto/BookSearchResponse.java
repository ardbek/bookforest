package com.ebook.bookforest.domain.book.dto;

import com.ebook.bookforest.domain.book.entity.Book;
import com.ebook.bookforest.domain.book.repository.BookCategorySummary;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public record BookSearchResponse(
    Long bookId,
    String title,
    String subTitle,
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
        Book book,
        BookCategorySummary category,
        List<BookPlatformSummaryResponse> libraryPlatforms,
        List<BookPlatformSummaryResponse> subscriptionPlatforms,
        boolean favoriteYn
    ) {
        return from(
            book,
            category,
            libraryPlatforms,
            subscriptionPlatforms,
            Collections.emptyList(),
            Collections.emptyList(),
            favoriteYn
        );
    }

    public static BookSearchResponse from(
        Book book,
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
            book.getId(),
            book.getTitle(),
            book.getSubTitle(),
            book.getAuthor(),
            book.getPublisher(),
            book.getPublishedDate(),
            book.getIsbn13(),
            book.getIsbn10(),
            book.getCoverImgUrl(),
            book.getDescription(),
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
