package com.ebook.bookforest.domain.user.dto;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.domain.user.entity.FavoriteBook;
import java.time.LocalDate;

/**
 * 즐겨찾기 도서 목록 응답 항목
 */
public record FavoriteBookResponse(
    Long bookOriginId,
    Long platformId,
    String platformCode,
    String platformName,
    /** 플랫폼 원본 제목 */
    String title,
    /** 플랫폼 원본 저자 */
    String author,
    /** 플랫폼 원본 출판사 */
    String publisher,
    LocalDate publishedDate,
    String coverImgUrl
) {

    public static FavoriteBookResponse from(FavoriteBook favoriteBook) {
        BookOrigin bookOrigin = favoriteBook.getBookOrigin();
        Platform platform = bookOrigin.getPlatform();

        return new FavoriteBookResponse(
            bookOrigin.getId(),
            platform.getId(),
            platform.getPlatformCode(),
            platform.getPlatformName(),
            bookOrigin.getTitleRaw(),
            bookOrigin.getAuthorRaw(),
            bookOrigin.getPublisherRaw(),
            bookOrigin.getPublishedDate(),
            bookOrigin.getCoverImgUrl()
        );
    }
}
