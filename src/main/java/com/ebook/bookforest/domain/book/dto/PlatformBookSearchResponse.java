package com.ebook.bookforest.domain.book.dto;

import com.ebook.bookforest.domain.book.enums.ContentType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * 플랫폼별 검색 응답
 */
public record PlatformBookSearchResponse(
    long totalElements,
    List<Group> groups
) {

    /** 플랫폼별 검색 결과 그룹 */
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

    /** 플랫폼 도서 검색 항목 */
    public record Item(
        @JsonProperty("sourceBookId") String platformBookId,
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
        @JsonProperty("favoriteYn") boolean favorite
    ) {
    }
}
