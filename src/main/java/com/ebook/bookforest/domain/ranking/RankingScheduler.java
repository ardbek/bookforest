package com.ebook.bookforest.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RankingScheduler {
    private final RankingService rankings;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        rankings.initialize();
    }

    @Scheduled(cron = "${app.ranking.refresh-cron:0 0 0,6,12,18 * * *}", zone = "Asia/Seoul")
    public void refresh() {
        for (RankingType type : RankingType.values()) rankings.refresh(type);
    }
}
