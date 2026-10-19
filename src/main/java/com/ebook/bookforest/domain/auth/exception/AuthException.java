package com.ebook.bookforest.domain.auth.exception;

import com.ebook.bookforest.global.exception.BusinessException;

/**
 * 인증 도메인 예외, 인증 오류 코드만 허용
 */
public class AuthException extends BusinessException {

    public AuthException(AuthErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(AuthErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
