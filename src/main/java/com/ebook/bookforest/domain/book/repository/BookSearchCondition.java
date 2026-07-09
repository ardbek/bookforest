package com.ebook.bookforest.domain.book.repository;

import com.ebook.bookforest.domain.book.enums.BookSearchType;
import java.util.List;

public record BookSearchCondition(
    String keyword,
    BookSearchType searchType,
    List<Long> categoryIds,
    List<Long> libraryIds,
    List<Long> libraryPlatformIds,
    List<Long> subscriptionPlatformIds
) {
}
