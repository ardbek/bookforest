package com.ebook.bookforest.domain.book.dto;

import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;
import com.ebook.bookforest.domain.book.enums.ContentType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * 플랫폼 도서 상세 응답
 */
public record PlatformBookDetailResponse(
    Long platformId,
    String platformCode,
    String platformName,
    String platformType,
    /** 플랫폼이 부여한 도서 식별값, JSON 필드명은 sourceBookId 유지 */
    @JsonProperty("sourceBookId") String platformBookId,
    /** 원본 도서 ID */
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
    /** 도서 즐겨찾기 여부, JSON 필드명은 favoriteYn 유지 */
    @JsonProperty("favoriteYn") boolean favorite,
    String description,
    List<LibraryItem> libraries,
    List<RelatedBook> relatedBooks
) {

    /** 플랫폼에 속한 도서관별 보유 항목(모든 이용 상태) */
    public record LibraryItem(
        Long libraryId,
        String libraryCode,
        String libraryName,
        String siteUrl,
        String bookUrl,
        String coverImgUrl,
        AvailabilityStatus availabilityStatus,
        @JsonProperty("availableYn") boolean available,
        /** 도서관 즐겨찾기 여부, 비로그인이면 null */
        @JsonProperty("favoriteYn") Boolean favorite
    ) {
    }

    /** 다른 플랫폼의 같은 도서 */
    public record RelatedBook(
        Long platformId,
        @JsonProperty("sourceBookId") String platformBookId,
        String platformName,
        String platformType,
        String title,
        String author,
        /** ISBN 또는 TITLE_AUTHOR */
        String matchType
    ) {
    }
}
