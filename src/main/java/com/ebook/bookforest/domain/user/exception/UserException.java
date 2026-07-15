package com.ebook.bookforest.domain.user.exception;

import com.ebook.bookforest.global.exception.BusinessException;

public class UserException extends BusinessException {

    public UserException(UserErrorCode errorCode) {
        super(errorCode);
    }

    public UserException(
        UserErrorCode errorCode,
        Throwable cause
    ) {
        super(errorCode, cause);
    }

    @Override
    public UserErrorCode getErrorCode() {
        return (UserErrorCode) super.getErrorCode();
    }
}
