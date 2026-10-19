package com.ebook.bookforest.domain.category.entity;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 원본 도서와 플랫폼 카테고리 연결
 * 수집기가 등록하고 이 애플리케이션은 도서 검색 조회에서만 사용
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_origin_category_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_origin_id", nullable = false)
    private BookOrigin bookOrigin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_category_id", nullable = false)
    private PlatformCategory platformCategory;

    @Builder
    private BookOriginCategory(BookOrigin bookOrigin, PlatformCategory platformCategory) {
        this.bookOrigin = Objects.requireNonNull(bookOrigin, "원본 도서는 필수입니다.");
        this.platformCategory = Objects.requireNonNull(platformCategory, "플랫폼 카테고리는 필수입니다.");
    }
}
