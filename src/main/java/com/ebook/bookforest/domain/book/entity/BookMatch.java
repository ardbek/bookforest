package com.ebook.bookforest.domain.book.entity;

import com.ebook.bookforest.domain.book.enums.MatchType;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "book_match")
public class BookMatch extends BaseTimeEntity {

    /* 매칭 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_match_id")
    private Long id;

    /* 대표 도서 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "book_id",
        nullable = false
    )
    private Book book;

    /* 원본 도서 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "book_origin_id",
        nullable = false
    )
    private BookOrigin bookOrigin;

    /* 매칭 타입 */
    @Enumerated(EnumType.STRING)
    @Column(
        name = "match_type",
        nullable = false,
        length = 30
    )
    private MatchType matchType;

    /* 매칭 점수 */
    @Column(
        name = "match_score",
        precision = 5,
        scale = 4
    )
    private BigDecimal matchScore;

    /* 매칭 시각 */
    @Column(
        name = "matched_at",
        nullable = false
    )
    private LocalDateTime matchedAt;

    public static BookMatch create(
        Book book,
        BookOrigin bookOrigin,
        MatchType matchType,
        BigDecimal matchScore
    ) {
        return BookMatch.builder()
            .book(book)
            .bookOrigin(bookOrigin)
            .matchType(matchType)
            .matchScore(matchScore)
            .matchedAt(LocalDateTime.now())
            .build();
    }
}