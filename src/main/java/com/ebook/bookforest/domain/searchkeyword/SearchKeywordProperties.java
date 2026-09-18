package com.ebook.bookforest.domain.searchkeyword;

import java.time.Duration;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("app.search-ranking")
public class SearchKeywordProperties {
    private String actorSecret;
    private boolean cookieSecure = true;
    private Duration redisTimeout = Duration.ofMillis(250);
    private int minimumCount = 3;
    private int eventTtlSeconds = 600;
    private int cooldownSeconds = 60;
    private int snapshotTtlSeconds = 180;
    private int bucketTtlSeconds = 691200;
    private int actorMinuteLimit = 10;
    private int actorHourLimit = 100;
    private int apiActorMinuteLimit = 60;
    private int apiIpMinuteLimit = 300;
    private List<String> blockedKeywords = List.of();
    private List<String> trustedProxies = List.of();
}
