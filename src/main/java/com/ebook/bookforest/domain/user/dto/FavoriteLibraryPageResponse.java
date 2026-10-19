package com.ebook.bookforest.domain.user.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 즐겨찾기 도서관 목록 페이지 응답
 */
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

    /** 즐겨찾기 도서관 목록과 페이지 정보 */
    public record FavoriteLibraryPage(
        List<FavoriteLibraryResponse> content,
        long totalElements,
        boolean last
    ) {
    }
}
