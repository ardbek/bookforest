package com.ebook.bookforest.domain.searchkeyword.config;

import java.time.Duration;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 인기 검색어 집계 설정(app.search-ranking)
 */
@Data
@Component
@ConfigurationProperties("app.search-ranking")
public class SearchKeywordProperties {
    /** 사용자 식별값 해시용 비밀값, 필수 */
    private String actorSecret;
    /** 익명 사용자 쿠키의 Secure 속성 */
    private boolean cookieSecure = true;
    /** 전용 Redis 연결의 연결, 명령 타임아웃 */
    private Duration redisTimeout = Duration.ofMillis(250);
    /** 인기 검색어로 노출할 최소 검색 횟수(24시간 합계) */
    private int minimumCount = 3;
    /** 같은 검색 이벤트 중복 제거 유지 시간 */
    private int eventTtlSeconds = 600;
    /** 같은 사용자의 같은 검색어를 다시 세지 않는 시간 */
    private int cooldownSeconds = 60;
    /** 인기 검색어 스냅샷 유지 시간, 갱신이 멈추면 이후 빈 결과 */
    private int snapshotTtlSeconds = 180;
    /** 시간 단위 집계 버킷 유지 시간(8일) */
    private int bucketTtlSeconds = 691200;
    /** 사용자별 집계 시도 한도(분당, 시간당) */
    private int actorMinuteLimit = 10;
    private int actorHourLimit = 100;
    /** 검색 API 요청 한도(사용자별, IP별, 분당) */
    private int apiActorMinuteLimit = 60;
    private int apiIpMinuteLimit = 300;
    /** 인기 검색어에서 제외할 차단어, 정규화 후 비교 */
    private List<String> blockedKeywords = List.of();
    /** X-Forwarded-For를 신뢰할 프록시 주소 */
    private List<String> trustedProxies = List.of();
}
