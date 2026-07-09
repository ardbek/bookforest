package com.ebook.bookforest.domain.platform.dto;

import com.ebook.bookforest.domain.platform.entity.Platform;

public record PlatformResponse(
    Long id,
    String platformCode,
    String platformName,
    String logoUrl,
    boolean subscribe
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
