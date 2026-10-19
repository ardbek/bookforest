package com.ebook.bookforest.global.security;

import com.ebook.bookforest.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 보안 필터 영역의 인증, 인가 오류 코드
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum SecurityErrorCode implements ErrorCode {

    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "authentication_required", "인증이 필요합니다."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "invalid_access_token", "Access Token이 올바르지 않습니다."),
    EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "expired_access_token", "Access Token이 만료되었습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "access_denied", "요청한 리소스에 접근할 권한이 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
