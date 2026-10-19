package com.ebook.bookforest.domain.user.entity;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 즐겨찾기한 원본 도서, 사용자와 도서 조합당 한 건
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "favorite_book",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_favorite_book_user_origin",
        columnNames = {"user_id", "book_origin_id"}
    )
)
public class FavoriteBook extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "favorite_book_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_origin_id", nullable = false)
    private BookOrigin bookOrigin;

    private FavoriteBook(User user, BookOrigin bookOrigin) {
        this.user = Objects.requireNonNull(user, "사용자는 필수입니다.");
        this.bookOrigin = Objects.requireNonNull(bookOrigin, "원본 도서는 필수입니다.");
    }

    /** 즐겨찾기 도서 생성 */
    public static FavoriteBook create(User user, BookOrigin bookOrigin) {
        return new FavoriteBook(user, bookOrigin);
    }
}
