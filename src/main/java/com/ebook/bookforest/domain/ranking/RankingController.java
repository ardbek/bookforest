package com.ebook.bookforest.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class RankingController {
    private final RankingService rankings;

    @GetMapping("/popular")
    public RankingResponse popular() {
        return rankings.get(RankingType.POPULAR);
    }

    @GetMapping("/trending")
    public RankingResponse trending() {
        return rankings.get(RankingType.TRENDING);
    }
}
