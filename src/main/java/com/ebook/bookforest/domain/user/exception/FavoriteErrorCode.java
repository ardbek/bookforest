package com.ebook.bookforest.domain.user.exception;

import com.ebook.bookforest.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum FavoriteErrorCode implements ErrorCode {

    BOOK_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "book_not_found",
        "도서를 찾을 수 없습니다."
    ),

    LIBRARY_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "library_not_found",
        "도서관을 찾을 수 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    FavoriteErrorCode(
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
