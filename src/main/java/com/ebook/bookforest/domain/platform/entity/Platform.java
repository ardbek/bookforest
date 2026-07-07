package com.ebook.bookforest.domain.platform.entity;

import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "platform")
public class Platform extends BaseTimeEntity {

    /* 플랫폼 id(PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "platform_id")
    private Long id;

    /* 플랫폼 코드 */
    @Column(name = "platform_code")
    private String platformCode;

    /* 플랫폼명 */
    @Column(name = "platform_name")
    private String platformName;

    /* 무료 도서관 지원 여부 */
    @Column(name = "supports_library", nullable = false)
    @Builder.Default
    private boolean supportsLibrary = false;

    /* 구독 플랫폼 지원 여부 */
    @Column(name = "supports_subscription", nullable = false)
    @Builder.Default
    private boolean supportsSubscription = false;

}
