package com.ebook.bookforest.domain.searchkeyword.controller;

import com.ebook.bookforest.domain.searchkeyword.dto.PopularSearchKeywordResponse;
import com.ebook.bookforest.domain.searchkeyword.service.SearchKeywordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인기 검색어 API
 */
@RestController
@RequiredArgsConstructor
public class PopularSearchKeywordController {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 20;

    private final SearchKeywordService searchKeywordService;

    /** 인기 검색어 조회(최근 24시간), 개수가 1~20 범위를 벗어나면 본문 없는 400 */
    @GetMapping("/api/search-keywords/popular")
    public ResponseEntity<PopularSearchKeywordResponse> getPopularKeywords(
        @RequestParam(defaultValue = "10") int limit
    ) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(searchKeywordService.getPopularKeywords(limit));
    }
}
