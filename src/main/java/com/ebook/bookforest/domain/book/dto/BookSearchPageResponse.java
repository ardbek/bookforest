package com.ebook.bookforest.domain.book.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 통합 검색 페이지 응답
 */
public record BookSearchPageResponse(
    BookSearchPage books
) {

    public static BookSearchPageResponse from(Page<BookSearchResponse> page) {
        return new BookSearchPageResponse(BookSearchPage.from(page));
    }

    /** 도서 목록과 페이지 정보 */
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
