package com.ebook.bookforest.domain.ranking;

public enum RankingType {
    POPULAR, TRENDING;

    public String cacheKey() {
        return "bookforest:ranking:" + (this == POPULAR ? "popular" : "hot-trend");
    }
}
