package com.ebook.bookforest.domain.book.entity;

import com.ebook.bookforest.domain.book.enums.ContentType;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플랫폼에서 수집한 원본 도서
 * 수집기가 등록하고 이 애플리케이션은 조회만 담당, 같은 책이라도 플랫폼마다 한 건씩 존재
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "book_origin",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_book_origin_platform_book",
        columnNames = {"platform_id", "content_type", "platform_book_id"}
    )
)
public class BookOrigin extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_origin_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    /** 플랫폼이 부여한 도서 식별값, 같은 값이라도 콘텐츠 종류가 다르면 다른 도서 */
    @Column(name = "platform_book_id", nullable = false, length = 200)
    private String platformBookId;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 30)
    private ContentType contentType;

    /** 플랫폼 원본 제목 */
    @Lob
    @Column(name = "title_raw", nullable = false, columnDefinition = "TEXT")
    private String titleRaw;

    @Lob
    @Column(name = "author_raw", columnDefinition = "TEXT")
    private String authorRaw;

    @Lob
    @Column(name = "publisher_raw", columnDefinition = "TEXT")
    private String publisherRaw;

    /** 플랫폼 원본 카테고리 문자열 */
    @Lob
    @Column(name = "category_raw", columnDefinition = "TEXT")
    private String categoryRaw;

    /** 플랫폼 원본 출판일 문자열, 형식이 플랫폼마다 다름 */
    @Column(name = "published_date_raw", length = 100)
    private String publishedDateRaw;

    @Column(name = "isbn13_raw", length = 50)
    private String isbn13Raw;

    @Column(name = "isbn10_raw", length = 50)
    private String isbn10Raw;

    /** 검색용 정규화 제목, FULLTEXT 인덱스 대상 */
    @Column(name = "title_norm", nullable = false, length = 500)
    private String titleNorm;

    /** 검색용 정규화 저자, FULLTEXT 인덱스 대상 */
    @Column(name = "author_norm", length = 500)
    private String authorNorm;

    /** 검색용 정규화 출판사, FULLTEXT 인덱스 대상 */
    @Column(name = "publisher_norm", length = 300)
    private String publisherNorm;

    /** 원본 출판일 문자열에서 해석한 출판일 */
    @Column(name = "published_date")
    private LocalDate publishedDate;

    /** 플랫폼 도서 상세 페이지 URL */
    @Column(name = "book_url", length = 1000)
    private String bookUrl;

    @Column(name = "cover_img_url", length = 1000)
    private String coverImgUrl;

    @Lob
    @Column(name = "description_raw", columnDefinition = "TEXT")
    private String descriptionRaw;

    /** 수집기가 처음 발견한 시각 */
    @Column(name = "first_seen_at")
    private LocalDateTime firstSeenAt;

    /** 수집기가 마지막으로 발견한 시각 */
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    /** 서비스 노출 여부, 플랫폼에서 사라진 도서는 수집기가 비활성 처리 */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** 콘텐츠 종류 미지정 시 기본값 EBOOK */
    @Builder
    private BookOrigin(
        Platform platform,
        String platformBookId,
        ContentType contentType,
        String titleRaw,
        String authorRaw,
        String publisherRaw,
        String categoryRaw,
        String publishedDateRaw,
        String isbn13Raw,
        String isbn10Raw,
        String titleNorm,
        String authorNorm,
        String publisherNorm,
        LocalDate publishedDate,
        String bookUrl,
        String coverImgUrl,
        String descriptionRaw
    ) {
        this.platform = Objects.requireNonNull(platform, "플랫폼은 필수입니다.");
        this.platformBookId = Objects.requireNonNull(platformBookId, "플랫폼 도서 식별값은 필수입니다.");
        this.contentType = contentType == null ? ContentType.EBOOK : contentType;
        this.titleRaw = Objects.requireNonNull(titleRaw, "원본 제목은 필수입니다.");
        this.authorRaw = authorRaw;
        this.publisherRaw = publisherRaw;
        this.categoryRaw = categoryRaw;
        this.publishedDateRaw = publishedDateRaw;
        this.isbn13Raw = isbn13Raw;
        this.isbn10Raw = isbn10Raw;
        this.titleNorm = Objects.requireNonNull(titleNorm, "정규화 제목은 필수입니다.");
        this.authorNorm = authorNorm;
        this.publisherNorm = publisherNorm;
        this.publishedDate = publishedDate;
        this.bookUrl = bookUrl;
        this.coverImgUrl = coverImgUrl;
        this.descriptionRaw = descriptionRaw;
    }
}
