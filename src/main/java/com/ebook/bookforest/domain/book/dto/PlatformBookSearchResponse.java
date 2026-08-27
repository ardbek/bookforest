package com.ebook.bookforest.domain.book.dto;

import java.time.LocalDate;
import java.util.List;

public record PlatformBookSearchResponse(
    long totalElements,
    List<Group> groups
) {

    public record Group(
        Long platformId,
        String platformCode,
        String platformName,
        String platformType,
        long totalElements,
        boolean last,
        List<Item> content
    ) {
    }

    public record Item(
        String sourceBookId,
        Long canonicalBookId,
        String title,
        String author,
        String publisher,
        LocalDate publishedDate,
        String isbn13,
        String coverImgUrl,
        String bookUrl,
        long libraryCount,
        long availableLibraryCount,
        boolean favoriteYn
    ) {
    }
}
