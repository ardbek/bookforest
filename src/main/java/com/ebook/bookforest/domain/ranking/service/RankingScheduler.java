package com.ebook.bookforest.domain.ranking.service;

import com.ebook.bookforest.domain.ranking.enums.RankingType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 도서 순위 갱신 스케줄러
 */
@Component
@RequiredArgsConstructor
public class RankingScheduler {

    private final RankingService rankingService;

    /** 애플리케이션 시작 직후 비어 있는 순위만 채움 */
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        rankingService.initialize();
    }

    /** 순위 전체 갱신(기본 하루 4번, 한국 시간 기준) */
    @Scheduled(cron = "${app.ranking.refresh-cron:0 0 0,6,12,18 * * *}", zone = "Asia/Seoul")
    public void refresh() {
        for (RankingType type : RankingType.values()) {
            rankingService.refresh(type);
        }
    }
}
