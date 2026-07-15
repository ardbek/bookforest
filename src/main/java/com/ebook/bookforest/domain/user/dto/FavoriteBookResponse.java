package com.ebook.bookforest.domain.user.dto;

import com.ebook.bookforest.domain.book.entity.Book;
import com.ebook.bookforest.domain.user.entity.FavoriteBook;
import java.time.LocalDate;

public record FavoriteBookResponse(
    Long bookId,
    String title,
    String author,
    String publisher,
    LocalDate publishedDate,
    String coverImgUrl
) {

    public static FavoriteBookResponse from(FavoriteBook favoriteBook) {
        Book book = favoriteBook.getBook();

        return new FavoriteBookResponse(
            book.getId(),
            book.getTitle(),
            book.getAuthor(),
            book.getPublisher(),
            book.getPublishedDate(),
            book.getCoverImgUrl()
        );
    }
}
