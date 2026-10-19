package com.ebook.bookforest.domain.book.dto;

import java.util.List;

/**
 * 통합 검색 요청 조건
 *
 * @param searchType 검색 대상(TITLE, AUTHOR, PUBLISHER), 없거나 알 수 없으면 전체
 */
public record BookSearchRequest(
    String keyword,
    String searchType,
    List<Long> categoryIds,
    List<Long> libraryIds,
    List<Long> libraryPlatformIds,
    List<Long> subscriptionPlatformIds
) {
}
