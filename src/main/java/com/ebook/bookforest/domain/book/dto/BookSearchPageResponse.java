package com.ebook.bookforest.domain.book.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record BookSearchPageResponse(
    BookSearchPage books
) {

    public static BookSearchPageResponse from(Page<BookSearchResponse> page) {
        return new BookSearchPageResponse(BookSearchPage.from(page));
    }

    public record BookSearchPage(
        List<BookSearchResponse> content,
        long totalElements,
        boolean last
    ) {

        public static BookSearchPage from(Page<BookSearchResponse> page) {
            return new BookSearchPage(
                page.getContent(),
                page.getTotalElements(),
                page.isLast()
            );
        }
    }
}
