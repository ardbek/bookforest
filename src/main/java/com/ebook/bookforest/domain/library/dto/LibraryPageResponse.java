package com.ebook.bookforest.domain.library.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 도서관 목록 페이지 응답
 */
public record LibraryPageResponse(
    LibraryPage libraries
) {

    public static LibraryPageResponse from(Page<LibraryResponse> page) {
        return new LibraryPageResponse(LibraryPage.from(page));
    }

    /** 도서관 목록과 페이지 정보 */
    public record LibraryPage(
        List<LibraryResponse> content,
        long totalElements,
        boolean last
    ) {

        public static LibraryPage from(Page<LibraryResponse> page) {
            return new LibraryPage(
                page.getContent(),
                page.getTotalElements(),
                page.isLast()
            );
        }
    }
}
