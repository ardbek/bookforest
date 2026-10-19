package com.ebook.bookforest.domain.book.exception;

import com.ebook.bookforest.global.exception.BusinessException;

/**
 * 도서 도메인 예외, 도서 오류 코드만 허용
 */
public class BookException extends BusinessException {

    public BookException(BookErrorCode errorCode) {
        super(errorCode);
    }

    public BookException(BookErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
