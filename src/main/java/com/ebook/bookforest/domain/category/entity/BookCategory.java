package com.ebook.bookforest.domain.category.entity;

import com.ebook.bookforest.domain.book.entity.Book;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(
    name = "book_category",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_book_category",
            columnNames = {"book_id", "standard_category_id"}
        )
    },
    indexes = {
        @Index(
            name = "idx_book_category_category",
            columnList = "standard_category_id, book_id"
        )
    }
)
public class BookCategory extends BaseTimeEntity {

    /* 도서 카테고리 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_category_id")
    private Long id;

    /* 대표 도서 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    /* 표준 카테고리 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "standard_category_id", nullable = false)
    private StandardCategory standardCategory;

    /* 대표 카테고리 여부 */
    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean primary = false;
}
