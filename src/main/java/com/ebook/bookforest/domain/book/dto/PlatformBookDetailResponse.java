package com.ebook.bookforest.domain.book.dto;

import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;
import com.ebook.bookforest.domain.book.enums.ContentType;
import java.time.LocalDate;
import java.util.List;

public record PlatformBookDetailResponse(
    Long platformId,
    String platformCode,
    String platformName,
    String platformType,
    String sourceBookId,
    Long canonicalBookId,
    ContentType contentType,
    String title,
    String author,
    String publisher,
    LocalDate publishedDate,
    String isbn13,
    String coverImgUrl,
    String bookUrl,
    long libraryCount,
    long availableLibraryCount,
    boolean favoriteYn,
    String description,
    List<LibraryItem> libraries,
    List<RelatedBook> relatedBooks
) {

    public record LibraryItem(
        Long libraryId,
        String libraryCode,
        String libraryName,
        String siteUrl,
        String bookUrl,
        String coverImgUrl,
        AvailabilityStatus availabilityStatus,
        boolean availableYn,
        Boolean favoriteYn
    ) {
    }

    public record RelatedBook(
        Long platformId,
        String sourceBookId,
        String platformName,
        String platformType,
        String title,
        String author,
        String matchType
    ) {
    }
}
