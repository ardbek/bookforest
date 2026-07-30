package com.ebook.bookforest.domain.library.exception;

import com.ebook.bookforest.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum LibraryErrorCode implements ErrorCode {

    PLATFORM_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "platform_not_found",
            "플랫폼 정보가 없습니다."
    ),
    LIBRARY_NOT_SUPPORTED(
            HttpStatus.BAD_REQUEST,
            "library_not_supported",
            "지원하지 않는 도서관입니다."
    );


    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    LibraryErrorCode(
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
