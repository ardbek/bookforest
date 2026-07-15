package com.ebook.bookforest.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();

        log.warn("비즈니스 오류: code={}, exceptionType={}", errorCode.code(), e.getClass().getSimpleName());

        return ResponseEntity
            .status(errorCode.httpStatus())
            .body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception e) {
        ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;

        log.warn("잘못된 요청: exceptionType={}", e.getClass().getSimpleName());

        return ResponseEntity
            .status(errorCode.httpStatus())
            .body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        ErrorCode errorCode = CommonErrorCode.METHOD_NOT_ALLOWED;

        return ResponseEntity
            .status(errorCode.httpStatus())
            .body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
        ErrorCode errorCode = CommonErrorCode.RESOURCE_NOT_FOUND;

        return ResponseEntity
            .status(errorCode.httpStatus())
            .body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        log.error("처리되지 않은 서버 오류", e);

        ErrorCode errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity
            .status(errorCode.httpStatus())
            .body(ErrorResponse.from(errorCode));
    }
}
