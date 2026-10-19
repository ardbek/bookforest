package com.ebook.bookforest.domain.book.repository;

import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;

/**
 * 도서관 보유 현황 조회 결과, 도서관별 URL과 표지가 없으면 원본 도서 값
 */
public record BookLibraryHolding(
    Long bookOriginId,
    Long platformId,
    String platformCode,
    String platformName,
    Long libraryId,
    String libraryCode,
    String libraryName,
    String siteUrl,
    String bookUrl,
    String coverImgUrl,
    AvailabilityStatus availabilityStatus
) {
}
