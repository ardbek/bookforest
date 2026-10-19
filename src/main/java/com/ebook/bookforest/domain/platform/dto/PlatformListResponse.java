package com.ebook.bookforest.domain.platform.dto;

import com.ebook.bookforest.domain.platform.entity.Platform;
import java.util.List;

/**
 * 플랫폼 목록 응답
 */
public record PlatformListResponse(
    List<PlatformResponse> platforms
) {

    public static PlatformListResponse from(List<Platform> platforms) {
        return new PlatformListResponse(
            platforms.stream()
                .map(PlatformResponse::from)
                .toList()
        );
    }
}
