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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플랫폼에 속한 무료 전자도서관
 * 수집기가 등록하고 이 애플리케이션은 조회만 담당
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "library",
    indexes = {
        @Index(
            name = "idx_library_platform_visible",
            columnList = "platform_id, is_visible"
        )
    }
)
public class Library extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "library_id")
    private Long id;

    /** 도서관 서비스를 제공하는 플랫폼 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    /** 플랫폼이 부여한 도서관 식별 코드 */
    @Column(name = "library_code", nullable = false, length = 100)
    private String libraryCode;

    @Column(name = "library_name", nullable = false, length = 200)
    private String libraryName;

    /** 도서관 사이트 URL */
    @Column(name = "site_url", length = 200)
    private String siteUrl;

    @Lob
    @Column(name = "library_description")
    private String libraryDescription;

    /** 서비스 노출 여부 */
    @Column(name = "is_visible", nullable = false)
    private boolean visible = true;

    @Builder
    private Library(
        Platform platform,
        String libraryCode,
        String libraryName,
        String siteUrl,
        String libraryDescription
    ) {
        this.platform = Objects.requireNonNull(platform, "플랫폼은 필수입니다.");
        this.libraryCode = Objects.requireNonNull(libraryCode, "도서관 코드는 필수입니다.");
        this.libraryName = Objects.requireNonNull(libraryName, "도서관명은 필수입니다.");
        this.siteUrl = siteUrl;
        this.libraryDescription = libraryDescription;
    }
}
