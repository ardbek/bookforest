package com.ebook.bookforest.domain.book.dto;

import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;
import com.ebook.bookforest.domain.book.repository.BookLibraryHolding;

/**
 * 전자 도서관 보유 목록 응답
 * @param libraryId
 * @param libraryCode
 * @param libraryName
 * @param siteUrl
 * @param bookUrl
 * @param coverImgUrl
 * @param availabilityStatus
 * @param availableYn
 */
public record BookLibraryHoldingResponse(
    Long libraryId,
    String libraryCode,
    String libraryName,
    String siteUrl,
    String bookUrl,
    String coverImgUrl,
    AvailabilityStatus availabilityStatus,
    boolean availableYn
) {

    public static BookLibraryHoldingResponse from(BookLibraryHolding holding) {
        return new BookLibraryHoldingResponse(
            holding.libraryId(),
            holding.libraryCode(),
            holding.libraryName(),
            holding.siteUrl(),
            holding.bookUrl(),
            holding.coverImgUrl(),
            holding.availabilityStatus(),
            holding.availabilityStatus() == AvailabilityStatus.AVAILABLE
        );
    }
}
