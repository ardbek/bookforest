package com.ebook.bookforest.global.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {

    INVALID_REQUEST(
        HttpStatus.BAD_REQUEST,
        "invalid_request",
        "요청 값이 올바르지 않습니다."
    ),

    RESOURCE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "resource_not_found",
        "요청한 리소스를 찾을 수 없습니다."
    ),

    METHOD_NOT_ALLOWED(
        HttpStatus.METHOD_NOT_ALLOWED,
        "method_not_allowed",
        "지원하지 않는 HTTP 메서드입니다."
    ),

    INTERNAL_SERVER_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "internal_server_error",
        "서버 내부 오류가 발생했습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    CommonErrorCode(
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
