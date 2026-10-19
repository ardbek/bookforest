package com.ebook.bookforest.domain.book.exception;

import com.ebook.bookforest.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 도서 도메인 오류 코드
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum BookErrorCode implements ErrorCode {

    INVALID_PLATFORM_BOOK_REQUEST(
        HttpStatus.BAD_REQUEST,
        "invalid_platform_book_request",
        "플랫폼 도서 요청이 올바르지 않습니다."
    ),

    /** 플랫폼 도메인의 같은 코드와 메시지가 달라 API 계약 유지를 위해 별도 정의 */
    PLATFORM_NOT_FOUND(HttpStatus.NOT_FOUND, "platform_not_found", "존재하지 않는 플랫폼입니다."),

    PLATFORM_BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "platform_book_not_found", "존재하지 않는 플랫폼 도서입니다."),

    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "book_not_found", "도서를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
