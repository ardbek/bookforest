package com.ebook.bookforest.domain.user.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record FavoriteLibraryPageResponse(FavoriteLibraryPage favoriteLibraries) {

    public static FavoriteLibraryPageResponse from(Page<FavoriteLibraryResponse> page) {
        return new FavoriteLibraryPageResponse(
            new FavoriteLibraryPage(
                page.getContent(),
                page.getTotalElements(),
                page.isLast()
            )
        );
    }

    public record FavoriteLibraryPage(
        List<FavoriteLibraryResponse> content,
        long totalElements,
        boolean last
    ) {
    }
}
