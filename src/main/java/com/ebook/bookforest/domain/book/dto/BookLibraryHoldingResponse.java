package com.ebook.bookforest.domain.book.dto;

import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;
import com.ebook.bookforest.domain.book.repository.BookLibraryHolding;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 도서 상세의 도서관별 보유 항목
 */
public record BookLibraryHoldingResponse(
    Long libraryId,
    String libraryCode,
    String libraryName,
    String siteUrl,
    String bookUrl,
    String coverImgUrl,
    AvailabilityStatus availabilityStatus,
    /** 지금 이용 가능 여부, JSON 필드명은 API 계약에 따라 availableYn 유지 */
    @JsonProperty("availableYn") boolean available
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
