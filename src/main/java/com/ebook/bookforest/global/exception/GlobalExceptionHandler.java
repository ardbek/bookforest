package com.ebook.bookforest.global.exception;

import com.ebook.bookforest.global.exception.ErrorResponse.FieldErrorResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * MVC 요청 처리 중 생긴 예외를 공통 오류 응답으로 변환
 * 처리하지 않는 예외는 내부 메시지를 숨기고 서버 오류로 응답
 *
 * <p>로그 레벨
 * <ul>
 *   <li>4xx: 예상된 사용자 오류라 info</li>
 *   <li>5xx: 대응이 필요한 서버 오류라 error</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        if (errorCode.httpStatus().is5xxServerError()) {
            log.error("비즈니스 오류: code={}", errorCode.code(), exception);
        } else {
            log.info("비즈니스 오류: code={}, exceptionType={}", errorCode.code(), exception.getClass().getSimpleName());
        }
        return toResponse(errorCode);
    }

    /** 입력 검증 실패, 필드별 사유를 errors 배열로 응답 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationFailure(MethodArgumentNotValidException exception) {
        List<FieldErrorResponse> errors = exception.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> new FieldErrorResponse(fieldError.getField(), fieldError.getDefaultMessage()))
            .toList();
        log.info("입력 검증 실패: fields={}", errors.stream().map(FieldErrorResponse::field).toList());

        ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
        return ResponseEntity
            .status(errorCode.httpStatus())
            .body(ErrorResponse.of(errorCode, errors));
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        log.info("잘못된 요청: exceptionType={}", exception.getClass().getSimpleName());
        return toResponse(CommonErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return toResponse(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException exception) {
        return toResponse(CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("처리되지 않은 서버 오류", exception);
        return toResponse(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {
        return ResponseEntity
            .status(errorCode.httpStatus())
            .body(ErrorResponse.from(errorCode));
    }
}
