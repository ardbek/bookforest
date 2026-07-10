package com.ebook.bookforest.domain.book.repository;

import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;

/**
 * 전자도서관 보유 목록 조회 DTO
 * @param bookId
 * @param platformId
 * @param platformCode
 * @param platformName
 * @param libraryId
 * @param libraryCode
 * @param libraryName
 * @param siteUrl
 * @param bookUrl
 * @param coverImgUrl
 * @param availabilityStatus
 */
public record BookLibraryHolding(
    Long bookId,
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
