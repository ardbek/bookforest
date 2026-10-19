package com.ebook.bookforest.domain.library.exception;

import com.ebook.bookforest.global.exception.BusinessException;

/**
 * 도서관 도메인 예외, 도서관 오류 코드만 허용
 */
public class LibraryException extends BusinessException {

    public LibraryException(LibraryErrorCode errorCode) {
        super(errorCode);
    }

    public LibraryException(LibraryErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
