package com.ebook.bookforest.domain.ranking;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record RankingResponse(OffsetDateTime updatedAt, LocalDate sourceDate, List<Book> books) {
    public static RankingResponse empty() {
        return new RankingResponse(null, null, List.of());
    }

    public record Book(
        int rank,
        Long bookId,
        String isbn13,
        String title,
        String author,
        String publisher,
        String thumbnail,
        Long loanCount,
        List<Platform> platforms
    ) {
    }

    public record Platform(Long platformId, String platformName, String platformType) {
    }
}
