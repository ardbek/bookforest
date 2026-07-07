package com.ebook.bookforest.domain.library.entity;

import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "library",
    indexes = {
        @Index(
            name = "idx_library_platform_visible",
            columnList = "platform_id, is_visible"
        )
    }
)
public class Library extends BaseTimeEntity {

    /* 도서관 id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "library_id")
    private Long id;

    /* 플랫폼 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    /* 도서관 코드 */
    @Column(name = "library_code", nullable = false, length = 100)
    private String libraryCode;

    /* 도서관명*/
    @Column(name = "library_name", nullable = false, length = 200)
    private String libraryName;

    /* 도서관 사이트 url */
    @Column(name = "site_url", length = 200)
    private String siteUrl;

    /* 도서관 설명 */
    @Lob
    @Column(name = "library_description")
    private String libraryDescription;

    /* 노출 여부 */
    @Column(name = "is_visible", nullable = false)
    @Builder.Default
    private boolean visible = true;
}
