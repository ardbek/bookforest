package com.ebook.bookforest.global.exception;

/**
 * 공통 오류 응답
 *
 * <pre>
 * {
 *   "code": "invalid_refresh_token",
 *   "message": "Refresh Token이 올바르지 않습니다."
 * }
 * </pre>
 */
public record ErrorResponse(
    String code,
    String message
) {

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.code(), errorCode.message());
    }
}
