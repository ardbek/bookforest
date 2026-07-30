package com.ebook.bookforest.domain.library.exception;

import com.ebook.bookforest.global.exception.BusinessException;

public class LibraryException extends BusinessException {

  public LibraryException(
          LibraryErrorCode errorCode
  ) {
    super(errorCode);
  }

  public LibraryException(
          LibraryErrorCode errorCode,
          Throwable cause
  ) {
    super(errorCode, cause);
  }

  @Override
  public LibraryErrorCode getErrorCode() {
    return (LibraryErrorCode) super.getErrorCode();
  }

}
