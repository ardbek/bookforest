package com.ebook.bookforest.domain.book.controller;

import com.ebook.bookforest.domain.book.dto.BookSearchPageResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchResponse;
import com.ebook.bookforest.domain.book.dto.PlatformBookDetailResponse;
import com.ebook.bookforest.domain.book.dto.PlatformBookSearchResponse;
import com.ebook.bookforest.domain.book.service.BookSearchService;
import com.ebook.bookforest.domain.book.service.PlatformBookService;
import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
import com.ebook.bookforest.domain.searchkeyword.SearchKeywordService;
import com.ebook.bookforest.domain.searchkeyword.SearchKeywordWebConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final PlatformBookService platformBookService;
    private final SearchKeywordService searchKeywords;

    // 일반 도서를 검색하고 검색어 집계에 결과를 전달한다.
    @GetMapping("/search")
    public BookSearchPageResponse searchBooks(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String searchType,
        @RequestParam(required = false) List<Long> categoryIds,
        @RequestParam(required = false) List<Long> libraryIds,
        @RequestParam(required = false) List<Long> libraryPlatformIds,
        @RequestParam(required = false) List<Long> subscriptionPlatformIds,
        @PageableDefault(size = 20) Pageable pageable,
        HttpServletRequest request
    ) {
        var result = bookSearchService.searchBooks(
            keyword,
            searchType,
            categoryIds,
            libraryIds,
            libraryPlatformIds,
            subscriptionPlatformIds,
            pageable
        );
        searchKeywords.record((String) request.getAttribute(SearchKeywordWebConfig.ACTOR),
            request.getHeader("X-Search-Event-Id"), keyword, pageable.getPageNumber(), result.books().totalElements());
        return result;
    }

    // 플랫폼별 도서를 검색하고 검색어 집계에 결과를 전달한다.
    @GetMapping("/search/platforms")
    public PlatformBookSearchResponse searchPlatformBooks(
        @RequestParam String keyword,
        @RequestParam(required = false) String searchType,
        @RequestParam(required = false) List<Long> categoryIds,
        @RequestParam String platformType,
        @RequestParam(required = false) List<Long> platformIds,
        @RequestParam(required = false) Long platformId,
        @RequestParam int page,
        @RequestParam int size,
        @AuthenticationPrincipal JwtPrincipal principal,
        HttpServletRequest request
    ) {
        var result = platformBookService.searchPlatformBooks(
            keyword,
            searchType,
            categoryIds,
            platformType,
            platformIds,
            platformId,
            page,
            size,
            principal == null ? null : principal.userId()
        );
        searchKeywords.record((String) request.getAttribute(SearchKeywordWebConfig.ACTOR),
            request.getHeader("X-Search-Event-Id"), keyword, page, result.totalElements());
        return result;
    }

    // 플랫폼의 원천 도서 상세를 조회한다.
    @GetMapping("/platforms/{platformId}/books/{sourceBookId}")
    public PlatformBookDetailResponse getPlatformBook(
        @PathVariable Long platformId,
        @PathVariable String sourceBookId,
        @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return platformBookService.getPlatformBook(
            platformId,
            sourceBookId,
            principal == null ? null : principal.userId()
        );
    }

    // 도서 ID로 상세 정보를 조회한다.
    @GetMapping("/{bookOriginId}")
    public BookSearchResponse getBook(@PathVariable Long bookOriginId) {
        return bookSearchService.getBook(bookOriginId);
    }
}
