package com.ebook.bookforest.domain.searchkeyword;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PopularSearchKeywordController {
    private final SearchKeywordService rankings;

    @GetMapping("/api/search-keywords/popular")
    public ResponseEntity<?> popular(@RequestParam(defaultValue = "10") int limit) {
        if (limit < 1 || limit > 20) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(rankings.get(limit));
    }
}
