package com.ebook.bookforest.domain.ranking.controller;

import com.ebook.bookforest.domain.ranking.dto.RankingResponse;
import com.ebook.bookforest.domain.ranking.enums.RankingType;
import com.ebook.bookforest.domain.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인기, 급상승 도서 순위 API
 */
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    /** 인기 도서 순위 조회(최근 30일 대출) */
    @GetMapping("/popular")
    public RankingResponse getPopularBooks() {
        return rankingService.get(RankingType.POPULAR);
    }

    /** 급상승 도서 순위 조회 */
    @GetMapping("/trending")
    public RankingResponse getTrendingBooks() {
        return rankingService.get(RankingType.TRENDING);
    }
}
