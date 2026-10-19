package com.ebook.bookforest.domain.platform.dto;

import com.ebook.bookforest.domain.platform.entity.Platform;

/**
 * 다른 도메인에 제공하는 플랫폼 조회 정보
 */
public record PlatformInfo(
    Long id,
    String platformCode,
    String platformName,
    boolean supportsLibrary,
    boolean supportsSubscription
) {

    public static PlatformInfo from(Platform platform) {
        return new PlatformInfo(
            platform.getId(),
            platform.getPlatformCode(),
            platform.getPlatformName(),
            platform.isSupportsLibrary(),
            platform.isSupportsSubscription()
        );
    }
}
