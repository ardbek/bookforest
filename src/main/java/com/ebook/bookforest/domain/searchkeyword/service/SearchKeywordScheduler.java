package com.ebook.bookforest.domain.searchkeyword.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 인기 검색어 스냅샷 갱신 스케줄러
 */
@Component
@RequiredArgsConstructor
public class SearchKeywordScheduler {

    private final SearchKeywordService searchKeywordService;

    /** 인기 검색어 스냅샷 갱신(기본 매분, UTC 기준) */
    @Scheduled(cron = "${app.search-ranking.refresh-cron:0 * * * * *}", zone = "UTC")
    public void refresh() {
        searchKeywordService.refresh();
    }
}
