package com.ebook.bookforest.domain.book.dto;

import java.util.List;

/**
 * 도서 상세의 도서관 플랫폼별 보유 현황
 */
public record BookLibraryPlatformHoldingResponse(
    Long platformId,
    String platformCode,
    String platformName,
    long libraryCount,
    List<BookLibraryHoldingResponse> libraries
) {

    public static BookLibraryPlatformHoldingResponse of(
        Long platformId,
        String platformCode,
        String platformName,
        List<BookLibraryHoldingResponse> libraries
    ) {
        return new BookLibraryPlatformHoldingResponse(
            platformId,
            platformCode,
            platformName,
            libraries.size(),
            libraries
        );
    }
}
