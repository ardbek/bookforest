package com.ebook.bookforest.domain.book.service;

import java.util.List;

final class BookSearchFilters {
    private BookSearchFilters() {}

    // 검색 필터에서 유효한 ID만 중복 없이 유지한다.
    static List<Long> sanitizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .toList();
    }
}
