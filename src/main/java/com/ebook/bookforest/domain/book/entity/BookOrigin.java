package com.ebook.bookforest.domain.book.entity;

import com.ebook.bookforest.domain.book.enums.ContentType;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "book_origin")
public class BookOrigin extends BaseTimeEntity {

    /* 원본 도서 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_origin_id")
    private Long id;

    /* 플랫폼 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "platform_id",
        nullable = false
    )
    private Platform platform;

    /* 플랫폼 도서 ID */
    @Column(
        name = "platform_book_id",
        nullable = false,
        length = 200
    )
    private String platformBookId;

    /* 콘텐츠 타입 */
    @Enumerated(EnumType.STRING)
    @Column(
        name = "content_type",
        nullable = false,
        length = 30
    )
    @Builder.Default
    private ContentType contentType = ContentType.EBOOK;

    /* 원본 제목 */
    @Lob
    @Column(
        name = "title_raw",
        nullable = false
    )
    private String titleRaw;

    /* 원본 저자 */
    @Lob
    @Column(name = "author_raw")
    private String authorRaw;

    /* 원본 출판사 */
    @Lob
    @Column(name = "publisher_raw")
    private String publisherRaw;

    /* 원본 카테고리 */
    @Lob
    @Column(name = "category_raw")
    private String categoryRaw;

    /* 원본 출판일 */
    @Column(
        name = "published_date_raw",
        length = 100
    )
    private String publishedDateRaw;

    /* 원본 ISBN13 */
    @Column(
        name = "isbn13_raw",
        length = 50
    )
    private String isbn13Raw;

    /* 원본 ISBN10 */
    @Column(
        name = "isbn10_raw",
        length = 50
    )
    private String isbn10Raw;

    /* 정규화 제목 */
    @Column(
        name = "title_norm",
        nullable = false,
        length = 500
    )
    private String titleNorm;

    /* 정규화 저자 */
    @Column(
        name = "author_norm",
        length = 500
    )
    private String authorNorm;

    /* 정규화 출판사 */
    @Column(
        name = "publisher_norm",
        length = 300
    )
    private String publisherNorm;

    /* 출판일 */
    @Column(name = "published_date")
    private LocalDate publishedDate;

    /* 플랫폼 상세 URL */
    @Column(
        name = "book_url",
        length = 1000
    )
    private String bookUrl;

    /* 표지 이미지 URL */
    @Column(
        name = "cover_img_url",
        length = 1000
    )
    private String coverImgUrl;

    /* 원본 설명 */
    @Lob
    @Column(name = "description_raw")
    private String descriptionRaw;

    /* 최초 수집일 */
    @Column(name = "first_seen_at")
    private LocalDateTime firstSeenAt;

    /* 마지막 수집일 */
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    /* 활성 여부 */
    @Column(
        name = "is_active",
        nullable = false
    )
    @Builder.Default
    private boolean active = true;
}