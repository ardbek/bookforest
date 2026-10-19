package com.ebook.bookforest.domain.platform.dto;

import com.ebook.bookforest.domain.platform.entity.Platform;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 플랫폼 목록 응답 항목
 */
public record PlatformResponse(
    Long id,
    String platformCode,
    String platformName,
    /** 플랫폼 로고 URL, 현재 미제공이라 항상 null */
    String logoUrl,
    /** 구독 서비스 제공 여부, JSON 필드명은 API 계약에 따라 subscribe 유지 */
    @JsonProperty("subscribe") boolean supportsSubscription
) {

    public static PlatformResponse from(Platform platform) {
        return new PlatformResponse(
            platform.getId(),
            platform.getPlatformCode(),
            platform.getPlatformName(),
            null,
            platform.isSupportsSubscription()
        );
    }
}
