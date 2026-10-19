package com.ebook.bookforest.domain.library.dto;

import com.ebook.bookforest.domain.library.entity.Library;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 도서관 목록 응답 항목
 */
public record LibraryResponse(
    Long libraryId,
    String libraryName,
    /** 도서관이 속한 플랫폼 코드 */
    String platform,
    /** 도서관 사이트 URL */
    String siteUrl,
    /**
     * 즐겨찾기 여부, JSON 필드명은 API 계약에 따라 favoriteYn 유지
     * 도서관 목록 조회에서는 계산하지 않아 항상 false
     */
    @JsonProperty("favoriteYn") boolean favorite
) {

    public static LibraryResponse from(Library library, boolean favorite) {
        return new LibraryResponse(
            library.getId(),
            library.getLibraryName(),
            library.getPlatform().getPlatformCode(),
            library.getSiteUrl(),
            favorite
        );
    }
}
