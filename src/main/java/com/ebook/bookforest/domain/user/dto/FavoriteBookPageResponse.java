package com.ebook.bookforest.domain.user.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record FavoriteBookPageResponse(FavoriteBookPage favoriteBooks) {

    public static FavoriteBookPageResponse from(Page<FavoriteBookResponse> page) {
        return new FavoriteBookPageResponse(
            new FavoriteBookPage(
                page.getContent(),
                page.getTotalElements(),
                page.isLast()
            )
        );
    }

    public record FavoriteBookPage(
        List<FavoriteBookResponse> content,
        long totalElements,
        boolean last
    ) {
    }
}
