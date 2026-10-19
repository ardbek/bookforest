package com.ebook.bookforest.global.exception;

import lombok.Getter;

/**
 * 도메인 예외의 상위 클래스
 * GlobalExceptionHandler가 오류 코드의 HTTP 상태와 공통 오류 응답으로 변환
 */
@Getter
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    protected BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
    }
}
