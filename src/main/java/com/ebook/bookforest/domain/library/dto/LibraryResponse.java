package com.ebook.bookforest.domain.library.dto;

import com.ebook.bookforest.domain.library.entity.Library;

public record LibraryResponse(
    Long libraryId,         // 도서관 id
    String libraryName,     // 도서관 명
    String platform,        // 플랫폼
    String siteUrl,         // 도서관 url
    boolean favoriteYn      // 즐겨찾기 여부
) {

    public static LibraryResponse from(Library library, boolean favoriteYn) {
        return new LibraryResponse(
            library.getId(),
            library.getLibraryName(),
            library.getPlatform().getPlatformCode(),
            library.getSiteUrl(),
            favoriteYn
        );
    }
}
