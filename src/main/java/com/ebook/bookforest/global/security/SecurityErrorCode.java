package com.ebook.bookforest.global.security;

import com.ebook.bookforest.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SecurityErrorCode implements ErrorCode {

    AUTHENTICATION_REQUIRED(
        HttpStatus.UNAUTHORIZED,
          "authentication_required",
        "인증이 필요합니다."
    ),

    INVALID_ACCESS_TOKEN(
        HttpStatus.UNAUTHORIZED,
          "invalid_access_token",
        "Access Token이 올바르지 않습니다."
    ),

    EXPIRED_ACCESS_TOKEN(
        HttpStatus.UNAUTHORIZED,
          "expired_access_token",
        "Access Token이 만료되었습니다."
    ),

    ACCESS_DENIED(
        HttpStatus.FORBIDDEN,
          "access_denied",
        "요청한 리소스에 접근할 권한이 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    SecurityErrorCode(
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
