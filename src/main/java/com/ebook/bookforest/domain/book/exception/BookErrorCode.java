package com.ebook.bookforest.domain.book.exception;

import com.ebook.bookforest.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum BookErrorCode implements ErrorCode {

    INVALID_PLATFORM_BOOK_REQUEST(
        HttpStatus.BAD_REQUEST,
        "invalid_platform_book_request",
        "플랫폼 도서 요청이 올바르지 않습니다."
    ),
    PLATFORM_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "platform_not_found",
        "존재하지 않는 플랫폼입니다."
    ),
    PLATFORM_BOOK_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "platform_book_not_found",
        "존재하지 않는 플랫폼 도서입니다."
    ),

    BOOK_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "book_not_found",
            "도서를 찾을 수 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    BookErrorCode(
            HttpStatus httpStatus,
            String code,
            String message
    ) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
