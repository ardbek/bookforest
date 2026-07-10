package com.ebook.bookforest.domain.book.dto;

import java.util.List;

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
