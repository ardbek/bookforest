package com.ebook.bookforest.domain.library.exception;

import com.ebook.bookforest.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 도서관 도메인 오류 코드
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum LibraryErrorCode implements ErrorCode {

    /** 무료 전자도서관을 지원하지 않는 플랫폼 요청 */
    LIBRARY_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "library_not_supported", "지원하지 않는 도서관입니다."),

    /** 없거나 숨김 처리된 도서관 */
    LIBRARY_NOT_FOUND(HttpStatus.NOT_FOUND, "library_not_found", "도서관을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
