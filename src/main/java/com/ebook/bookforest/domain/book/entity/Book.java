package com.ebook.bookforest.domain.book.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "book")
public class Book {

    /* 대표 도서 id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_id")
    private Long id;

    /* 도서명 */
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    /* 부제 */
    @Column(name = "sub_title", length = 500)
    private String subTitle;

    /* 저자 */
    @Column(name = "author", length = 500)
    private String author;

    /* 출판사 */
    @Column(name = "publisher", length = 300)
    private String publisher;

    /* 출판일 */
    @Column(name = "published_date")
    private LocalDate publishedDate;

    /* ISBN-13 */
    @Column(name = "isbn13", length = 13)
    private String isbn13;

    /* ISBN-10 */
    @Column(name = "isbn10", length = 10)
    private String isbn10;

    /* 도서 이미지 url */
    @Column(name = "cover_img_url", length = 1000)
    private String coverImgUrl;

    /* 도서 설명 */
    @Lob
    @Column(name = "description")
    private String description;

    /* 정규화 도서명 */
    @Column(name = "title_norm", nullable = false, length = 500)
    private String titleNorm;

    /* 정규화 저자 */
    @Column(name = "author_norm", length = 500)
    private String authorNorm;

    /* 정규화 출판사 */
    @Column(name = "publisher_norm", length = 300)
    private String publisherNorm;

}
