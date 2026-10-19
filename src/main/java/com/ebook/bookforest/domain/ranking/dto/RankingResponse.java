package com.ebook.bookforest.domain.ranking.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 인기, 급상승 도서 순위 응답
 * Redis에 JSON으로 저장하므로 필드명을 바꾸면 기존 캐시를 읽지 못함
 */
public record RankingResponse(
    /** 순위를 갱신한 시각, 순위가 없으면 null */
    OffsetDateTime updatedAt,
    /** 도서관 정보나루 순위 기준일, 급상승은 가장 최근 순위가 있는 날짜 */
    LocalDate sourceDate,
    List<Book> books
) {

    /** 아직 순위를 받지 못했거나 읽기에 실패했을 때의 빈 응답 */
    public static RankingResponse empty() {
        return new RankingResponse(null, null, List.of());
    }

    /** 순위 도서, 제목과 저자 등은 도서관 정보나루 값 그대로 사용 */
    public record Book(
        int rank,
        /** ISBN이 같은 원본 도서 중 첫 번째 ID, 서비스에 없으면 null */
        Long bookId,
        String isbn13,
        String title,
        String author,
        String publisher,
        String thumbnail,
        /** 대출 건수, 급상승 순위는 제공하지 않아 null */
        Long loanCount,
        List<Platform> platforms
    ) {
    }

    /** 순위 도서를 제공하는 플랫폼 */
    public record Platform(
        Long platformId,
        String platformName,
        /** LIBRARY 또는 SUBSCRIPTION */
        String platformType
    ) {
    }
}
