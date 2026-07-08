package com.ebook.bookforest.domain.category.entity;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(
    name = "book_origin_category",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_origin_platform_category",
            columnNames = {"book_origin_id", "platform_category_id"}
        )
    },
    indexes = {
        @Index(
            name = "idx_boc_platform_category",
            columnList = "platform_category_id"
        )
    }
)
public class BookOriginCategory extends BaseTimeEntity {

    /* 원본 도서 카테고리 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_origin_category_id")
    private Long id;

    /* 원본 도서 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_origin_id", nullable = false)
    private BookOrigin bookOrigin;

    /* 플랫폼 카테고리 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_category_id", nullable = false)
    private PlatformCategory platformCategory;
}