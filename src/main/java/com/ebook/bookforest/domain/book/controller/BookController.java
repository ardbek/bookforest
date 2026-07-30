package com.ebook.bookforest.domain.book.controller;

import com.ebook.bookforest.domain.book.dto.BookSearchPageResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchResponse;
import com.ebook.bookforest.domain.book.service.BookSearchService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/book")
@RequiredArgsConstructor
public class BookController {

    private final BookSearchService bookSearchService;

    /**
     * 도서 검색
     * @param keyword
     * @param searchType
     * @param categoryIds
     * @param libraryIds
     * @param libraryPlatformIds
     * @param subscriptionPlatformIds
     * @param pageable
     * @return
     */
    @GetMapping("/search")
    public BookSearchPageResponse searchBooks(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String searchType,
        @RequestParam(required = false) List<Long> categoryIds,
        @RequestParam(required = false) List<Long> libraryIds,
        @RequestParam(required = false) List<Long> libraryPlatformIds,
        @RequestParam(required = false) List<Long> subscriptionPlatformIds,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return bookSearchService.searchBooks(
            keyword,
            searchType,
            categoryIds,
            libraryIds,
            libraryPlatformIds,
            subscriptionPlatformIds,
            pageable
        );
    }

    @GetMapping("/{bookId}")
    public BookSearchResponse getBook(@PathVariable Long bookId) {
        return bookSearchService.getBook(bookId);
    }
}
