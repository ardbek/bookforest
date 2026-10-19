package com.ebook.bookforest.domain.user.entity;

import com.ebook.bookforest.domain.library.entity.Library;
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
 * 사용자가 즐겨찾기한 무료 전자도서관, 사용자와 도서관 조합당 한 건
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "favorite_library",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_favorite_library_user_library",
        columnNames = {"user_id", "library_id"}
    )
)
public class FavoriteLibrary extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "favorite_library_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    private Library library;

    private FavoriteLibrary(User user, Library library) {
        this.user = Objects.requireNonNull(user, "사용자는 필수입니다.");
        this.library = Objects.requireNonNull(library, "도서관은 필수입니다.");
    }

    /** 즐겨찾기 도서관 생성 */
    public static FavoriteLibrary create(User user, Library library) {
        return new FavoriteLibrary(user, library);
    }
}
