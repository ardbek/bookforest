package com.ebook.bookforest.domain.platform.entity;

import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 무료 전자도서관이나 구독 서비스를 제공하는 전자책 플랫폼
 * 수집기와 시드 SQL이 등록하고 이 애플리케이션은 조회만 담당
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "platform")
public class Platform extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "platform_id")
    private Long id;

    /** 외부 연동과 응답에서 플랫폼을 구분하는 키 */
    @Column(name = "platform_code")
    private String platformCode;

    @Column(name = "platform_name")
    private String platformName;

    /** 무료 전자도서관 서비스 제공 여부 */
    @Column(name = "supports_library", nullable = false)
    private boolean supportsLibrary;

    /** 구독 서비스 제공 여부 */
    @Column(name = "supports_subscription", nullable = false)
    private boolean supportsSubscription;

    /** DB의 null 허용에 맞춘 플랫폼 코드와 이름의 필수 검증 생략 */
    @Builder
    private Platform(
        String platformCode,
        String platformName,
        boolean supportsLibrary,
        boolean supportsSubscription
    ) {
        this.platformCode = platformCode;
        this.platformName = platformName;
        this.supportsLibrary = supportsLibrary;
        this.supportsSubscription = supportsSubscription;
    }
}
