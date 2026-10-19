package com.ebook.bookforest.domain.book.dto;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.enums.ContentType;
import com.ebook.bookforest.domain.book.enums.PlatformType;
import com.ebook.bookforest.domain.book.repository.BookCategorySummary;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * 통합 검색 결과와 도서 상세 응답
 * 목록에서는 보유 현황과 구독 링크를 비워 두고, 상세에서만 채움
 */
public record BookSearchResponse(
    Long bookOriginId,
    Long platformId,
    String platformCode,
    String platformName,
    /** LIBRARY 또는 SUBSCRIPTION, 둘 다 지원하지 않는 플랫폼은 null */
    String platformType,
    ContentType contentType,
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
    /** 이용 가능한 노출 도서관 수 합계 */
    long libraryCount,
    long libraryPlatformCount,
    long subscriptionPlatformCount,
    List<BookPlatformSummaryResponse> libraryPlatforms,
    List<BookPlatformSummaryResponse> subscriptionPlatforms,
    List<BookSubscriptionPlatformResponse> subscriptionPlatformLinks,
    List<BookLibraryPlatformHoldingResponse> libraryPlatformHoldings,
    /** 즐겨찾기 여부, 이 응답에서는 계산하지 않아 항상 false, JSON 필드명은 favoriteYn 유지 */
    @JsonProperty("favoriteYn") boolean favorite
) {

    public static BookSearchResponse from(
        BookOrigin bookOrigin,
        BookCategorySummary category,
        List<BookPlatformSummaryResponse> libraryPlatforms,
        List<BookPlatformSummaryResponse> subscriptionPlatforms,
        boolean favorite
    ) {
        return from(
            bookOrigin,
            category,
            libraryPlatforms,
            subscriptionPlatforms,
            Collections.emptyList(),
            Collections.emptyList(),
            favorite
        );
    }

    public static BookSearchResponse from(
        BookOrigin bookOrigin,
        BookCategorySummary category,
        List<BookPlatformSummaryResponse> libraryPlatforms,
        List<BookPlatformSummaryResponse> subscriptionPlatforms,
        List<BookSubscriptionPlatformResponse> subscriptionPlatformLinks,
        List<BookLibraryPlatformHoldingResponse> libraryPlatformHoldings,
        boolean favorite
    ) {
        long libraryCount = libraryPlatforms.stream()
            .mapToLong(BookPlatformSummaryResponse::itemCount)
            .sum();

        return new BookSearchResponse(
            bookOrigin.getId(),
            bookOrigin.getPlatform().getId(),
            bookOrigin.getPlatform().getPlatformCode(),
            bookOrigin.getPlatform().getPlatformName(),
            platformTypeName(bookOrigin.getPlatform()),
            bookOrigin.getContentType(),
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
            favorite
        );
    }

    private static String platformTypeName(Platform platform) {
        PlatformType platformType = PlatformType.of(platform.isSupportsLibrary(), platform.isSupportsSubscription());
        return platformType == null ? null : platformType.name();
    }
}
