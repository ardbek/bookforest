package com.ebook.bookforest.domain.platform.exception;

import com.ebook.bookforest.global.exception.BusinessException;

/**
 * 플랫폼 도메인 예외, 플랫폼 오류 코드만 허용
 */
public class PlatformException extends BusinessException {

    public PlatformException(PlatformErrorCode errorCode) {
        super(errorCode);
    }

    public PlatformException(PlatformErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
