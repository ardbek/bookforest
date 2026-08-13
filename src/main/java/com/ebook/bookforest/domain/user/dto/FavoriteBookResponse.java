package com.ebook.bookforest.domain.user.dto;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.user.entity.FavoriteBook;
import java.time.LocalDate;

public record FavoriteBookResponse(
    Long bookOriginId,
    Long platformId,
    String platformCode,
    String platformName,
    String title,
    String author,
    String publisher,
    LocalDate publishedDate,
    String coverImgUrl
) {

    public static FavoriteBookResponse from(FavoriteBook favoriteBook) {
        BookOrigin bookOrigin = favoriteBook.getBookOrigin();

        return new FavoriteBookResponse(
            bookOrigin.getId(),
            bookOrigin.getPlatform().getId(),
            bookOrigin.getPlatform().getPlatformCode(),
            bookOrigin.getPlatform().getPlatformName(),
            bookOrigin.getTitleRaw(),
            bookOrigin.getAuthorRaw(),
            bookOrigin.getPublisherRaw(),
            bookOrigin.getPublishedDate(),
            bookOrigin.getCoverImgUrl()
        );
    }
}
