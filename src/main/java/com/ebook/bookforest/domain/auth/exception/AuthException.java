package com.ebook.bookforest.domain.auth.exception;

import com.ebook.bookforest.global.exception.BusinessException;

public class AuthException extends BusinessException {

    public AuthException(
        AuthErrorCode errorCode
    ) {
        super(errorCode);
    }

    public AuthException(
        AuthErrorCode errorCode,
        Throwable cause
    ) {
        super(errorCode, cause);
    }

    @Override
    public AuthErrorCode getErrorCode() {
        return (AuthErrorCode) super.getErrorCode();
    }
}
