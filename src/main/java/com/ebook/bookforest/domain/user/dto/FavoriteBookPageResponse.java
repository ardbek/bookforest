package com.ebook.bookforest.domain.user.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 즐겨찾기 도서 목록 페이지 응답
 */
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

    /** 즐겨찾기 도서 목록과 페이지 정보 */
    public record FavoriteBookPage(
        List<FavoriteBookResponse> content,
        long totalElements,
        boolean last
    ) {
    }
}
