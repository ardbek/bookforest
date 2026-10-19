package com.ebook.bookforest.domain.ranking.enums;

/**
 * 도서 순위 종류
 */
public enum RankingType {

    /** 최근 30일 대출 순위 */
    POPULAR("popular"),

    /** 대출 급상승 순위 */
    TRENDING("hot-trend");

    private static final String CACHE_KEY_PREFIX = "bookforest:ranking:";

    private final String cacheKeySuffix;

    RankingType(String cacheKeySuffix) {
        this.cacheKeySuffix = cacheKeySuffix;
    }

    /** 순위 응답을 저장하는 Redis 키, 운영 중인 키와 같아야 하므로 변경 금지 */
    public String cacheKey() {
        return CACHE_KEY_PREFIX + cacheKeySuffix;
    }
}
