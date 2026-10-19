package com.ebook.bookforest.domain.auth.exception;

import com.ebook.bookforest.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 인증 도메인 오류 코드
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    USER_NOT_ACTIVE(HttpStatus.UNAUTHORIZED, "user_not_active", "활성화된 사용자를 찾을 수 없습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "invalid_refresh_token", "Refresh Token이 올바르지 않습니다."),
    REFRESH_TOKEN_NOT_FOUND(
        HttpStatus.UNAUTHORIZED,
        "refresh_token_not_found",
        "Refresh Token 로그인 세션을 찾을 수 없습니다."
    ),
    REFRESH_TOKEN_REUSE_DETECTED(
        HttpStatus.UNAUTHORIZED,
        "refresh_token_reuse_detected",
        "이미 사용된 Refresh Token이 다시 사용되었습니다."
    ),
    MISSING_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "missing_refresh_token", "Refresh Token 쿠키가 없습니다."),
    INVALID_ORIGIN(HttpStatus.FORBIDDEN, "invalid_origin", "허용되지 않은 요청 출처입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
