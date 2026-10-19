package com.ebook.bookforest.domain.platform.exception;

import com.ebook.bookforest.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 플랫폼 도메인 오류 코드
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum PlatformErrorCode implements ErrorCode {

    PLATFORM_NOT_FOUND(HttpStatus.NOT_FOUND, "platform_not_found", "플랫폼 정보가 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
