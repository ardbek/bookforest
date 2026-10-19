package com.ebook.bookforest.domain.book.controller;

import com.ebook.bookforest.domain.book.dto.BookSearchPageResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchRequest;
import com.ebook.bookforest.domain.book.dto.BookSearchResponse;
import com.ebook.bookforest.domain.book.dto.PlatformBookDetailResponse;
import com.ebook.bookforest.domain.book.dto.PlatformBookSearchRequest;
import com.ebook.bookforest.domain.book.dto.PlatformBookSearchResponse;
import com.ebook.bookforest.domain.book.service.BookSearchService;
import com.ebook.bookforest.domain.searchkeyword.config.SearchRequestInterceptor;
import com.ebook.bookforest.domain.searchkeyword.service.SearchKeywordService;
import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
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

/**
 * 도서 검색, 상세 API
 * 검색 요청은 인기 검색어 집계를 위해 검색어와 결과 수를 함께 기록
 */
@RestController
@RequestMapping("/api/book")
@RequiredArgsConstructor
public class BookController {

    /** 같은 검색을 중복 집계하지 않도록 프론트가 검색마다 보내는 식별값 헤더 */
    private static final String SEARCH_EVENT_ID_HEADER = "X-Search-Event-Id";

    private final BookSearchService bookSearchService;
    private final SearchKeywordService searchKeywordService;

    /** 통합 검색 */
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
        BookSearchRequest searchRequest = new BookSearchRequest(
            keyword,
            searchType,
            categoryIds,
            libraryIds,
            libraryPlatformIds,
            subscriptionPlatformIds
        );
        BookSearchPageResponse result = bookSearchService.searchBooks(searchRequest, pageable);

        recordSearchKeyword(request, keyword, pageable.getPageNumber(), result.books().totalElements());
        return result;
    }

    /** 플랫폼별 검색 */
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
        PlatformBookSearchRequest searchRequest = new PlatformBookSearchRequest(
            keyword,
            searchType,
            categoryIds,
            platformType,
            platformIds,
            platformId,
            page,
            size
        );
        PlatformBookSearchResponse result = bookSearchService.searchPlatformBooks(searchRequest, userIdOf(principal));

        recordSearchKeyword(request, keyword, page, result.totalElements());
        return result;
    }

    /** 플랫폼 도서 상세 조회 */
    @GetMapping("/platforms/{platformId}/books/{sourceBookId}")
    public PlatformBookDetailResponse getPlatformBook(
        @PathVariable Long platformId,
        @PathVariable("sourceBookId") String platformBookId,
        @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return bookSearchService.getPlatformBook(platformId, platformBookId, userIdOf(principal));
    }

    /** 도서 상세 조회 */
    @GetMapping("/{bookOriginId}")
    public BookSearchResponse getBook(@PathVariable Long bookOriginId) {
        return bookSearchService.getBook(bookOriginId);
    }

    private void recordSearchKeyword(HttpServletRequest request, String keyword, int page, long resultCount) {
        searchKeywordService.record(
            (String) request.getAttribute(SearchRequestInterceptor.ACTOR_ATTRIBUTE),
            request.getHeader(SEARCH_EVENT_ID_HEADER),
            keyword,
            page,
            resultCount
        );
    }

    private Long userIdOf(JwtPrincipal principal) {
        return principal == null ? null : principal.userId();
    }
}
