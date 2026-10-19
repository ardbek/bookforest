package com.ebook.bookforest.domain.book.enums;

import com.ebook.bookforest.domain.book.exception.BookErrorCode;
import com.ebook.bookforest.domain.book.exception.BookException;
import java.util.Locale;

/**
 * 도서를 제공하는 플랫폼 종류
 * ALL은 검색 요청에서만 쓰는 전체 조건
 */
public enum PlatformType {

    ALL,

    LIBRARY,

    SUBSCRIPTION;

    /**
     * 검색 요청 파라미터 해석(대소문자 무시)
     *
     * @throws BookException 값이 없거나 알 수 없는 종류인 경우 INVALID_PLATFORM_BOOK_REQUEST
     */
    public static PlatformType fromRequest(String value) {
        if (value == null) {
            throw new BookException(BookErrorCode.INVALID_PLATFORM_BOOK_REQUEST);
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BookException(BookErrorCode.INVALID_PLATFORM_BOOK_REQUEST);
        }
    }

    /**
     * 플랫폼 지원 기능으로 판단한 실제 종류
     * 두 기능을 모두 지원하면 구독 우선, 둘 다 지원하지 않으면 null
     */
    public static PlatformType of(boolean supportsLibrary, boolean supportsSubscription) {
        if (supportsSubscription) {
            return SUBSCRIPTION;
        }
        return supportsLibrary ? LIBRARY : null;
    }

    /** 요청한 종류에 실제 종류가 포함되는지 여부, 실제 종류가 없으면 false */
    public boolean matches(PlatformType actual) {
        return actual != null && (this == ALL || this == actual);
    }
}
