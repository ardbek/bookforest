package com.ebook.bookforest.domain.user.entity;

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
}
