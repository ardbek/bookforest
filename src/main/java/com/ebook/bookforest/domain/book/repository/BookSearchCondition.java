package com.ebook.bookforest.domain.book.repository;

import com.ebook.bookforest.domain.book.enums.BookSearchType;
import java.util.List;

/**
 * 원본 도서 검색 조건, 플랫폼 조건들은 OR로 결합
 */
public record BookSearchCondition(
    String keyword,
    BookSearchType searchType,
    List<Long> categoryIds,
    List<Long> libraryIds,
    List<Long> libraryPlatformIds,
    List<Long> subscriptionPlatformIds,
    /** 원본 도서의 플랫폼 ID, 플랫폼별 검색에서만 사용 */
    Long sourcePlatformId
) {
}
