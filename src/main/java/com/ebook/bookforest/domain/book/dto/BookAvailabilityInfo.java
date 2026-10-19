package com.ebook.bookforest.domain.book.dto;

import com.ebook.bookforest.domain.book.enums.PlatformType;
import java.util.List;

/**
 * 다른 도메인에 제공하는 원본 도서의 플랫폼 제공 현황
 */
public record BookAvailabilityInfo(
    Long bookOriginId,
    String isbn13,
    /** 도서를 제공하는 플랫폼, 도서관 플랫폼이 구독 플랫폼보다 먼저 */
    List<Platform> platforms
) {

    /** 도서를 제공하는 플랫폼 */
    public record Platform(Long platformId, String platformName, PlatformType platformType) {
    }
}
