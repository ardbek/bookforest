package com.ebook.bookforest.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 공통 오류 응답
 * 입력 검증 실패일 때만 errors 배열을 포함하고, 그 외에는 응답에 errors 필드가 나타나지 않음
 *
 * <pre>
 * {
 *   "code": "invalid_request",
 *   "message": "요청 값이 올바르지 않습니다.",
 *   "errors": [
 *     { "field": "keyword", "reason": "2자 이상 입력해야 합니다." }
 *   ]
 * }
 * </pre>
 */
public record ErrorResponse(
    String code,
    String message,
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<FieldErrorResponse> errors
) {

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.code(), errorCode.message(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorResponse> errors) {
        return new ErrorResponse(errorCode.code(), errorCode.message(), List.copyOf(errors));
    }

    /**
     * 필드별 검증 실패 사유
     *
     * @param reason 검증 어노테이션의 메시지
     */
    public record FieldErrorResponse(
        String field,
        String reason
    ) {
    }
}
