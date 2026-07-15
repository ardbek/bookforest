package com.ebook.bookforest.domain.user.exception;

import com.ebook.bookforest.global.exception.BusinessException;

public class FavoriteException extends BusinessException {

    public FavoriteException(FavoriteErrorCode errorCode) {
        super(errorCode);
    }

    @Override
    public FavoriteErrorCode getErrorCode() {
        return (FavoriteErrorCode) super.getErrorCode();
    }
}
