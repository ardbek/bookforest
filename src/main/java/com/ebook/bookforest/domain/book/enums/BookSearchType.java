package com.ebook.bookforest.domain.book.enums;

import java.util.Locale;

public enum BookSearchType {
    ALL,
    TITLE,
    AUTHOR,
    PUBLISHER;

    public static BookSearchType from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return ALL;
        }

        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "TITLE", "제목", "제목 검색" -> TITLE;
            case "AUTHOR", "저자", "저자 검색" -> AUTHOR;
            case "PUBLISHER", "출판사", "출판사 검색" -> PUBLISHER;
            default -> ALL;
        };
    }
}
