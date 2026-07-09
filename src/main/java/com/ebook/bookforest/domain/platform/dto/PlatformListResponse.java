package com.ebook.bookforest.domain.platform.dto;

import java.util.List;

public record PlatformListResponse(
    List<PlatformResponse> platforms
) {
}
