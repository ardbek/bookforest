package com.ebook.bookforest.domain.user.exception;

import com.ebook.bookforest.global.exception.BusinessException;

/**
 * 사용자 도메인 예외, 사용자 오류 코드만 허용
 */
public class UserException extends BusinessException {

    public UserException(UserErrorCode errorCode) {
        super(errorCode);
    }

    public UserException(UserErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
