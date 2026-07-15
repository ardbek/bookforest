package com.ebook.bookforest.domain.user.exception;

import com.ebook.bookforest.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "user_not_found",
        "사용자를 찾을 수 없습니다."
    ),

    USER_ALREADY_WITHDRAWN(
        HttpStatus.CONFLICT,
        "user_already_withdrawn",
        "이미 탈퇴한 사용자입니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    UserErrorCode(
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
