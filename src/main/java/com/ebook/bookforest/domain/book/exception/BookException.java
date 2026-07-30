package com.ebook.bookforest.domain.book.exception;

import com.ebook.bookforest.global.exception.BusinessException;

public class BookException extends BusinessException {

    public BookException(BookErrorCode errorCode) {
        super(errorCode);
    }

    public BookException(BookErrorCode errorCode, Throwable cause){
        super(errorCode, cause);
    }

    @Override
    public BookErrorCode getErrorCode(){
        return (BookErrorCode) super.getErrorCode();
    }

}
