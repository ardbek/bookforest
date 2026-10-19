package com.ebook.bookforest.domain.searchkeyword.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 인기 검색어 응답
 * Redis 스냅샷에도 같은 JSON으로 저장하므로 필드명을 바꾸면 기존 스냅샷을 읽지 못함
 */
public record PopularSearchKeywordResponse(
    /** 스냅샷을 만든 시각, 스냅샷이 없으면 null */
    OffsetDateTime generatedAt,
    /** 집계 기간, ISO-8601 기간 형식 */
    String window,
    List<Item> items
) {

    /** 최근 24시간 집계 기간 */
    public static final String WINDOW_24_HOURS = "PT24H";

    /** 스냅샷이 없거나 읽기에 실패했을 때의 빈 응답 */
    public static PopularSearchKeywordResponse empty() {
        return new PopularSearchKeywordResponse(null, WINDOW_24_HOURS, List.of());
    }

    /** 순위별 검색어 */
    public record Item(int rank, String keyword) {
    }
}
