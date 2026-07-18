package com.ebook.bookforest.global.security.oauth;

import com.ebook.bookforest.global.exception.ErrorCode;
import java.util.Arrays;
import org.springframework.http.HttpStatus;

/**
 * 소셜 로그인 에러 코드
 */
public enum OAuth2LoginErrorCode implements ErrorCode {
    ACCESS_DENIED(
        HttpStatus.UNAUTHORIZED,
        "access_denied",
        "소셜 로그인 동의가 취소되었습니다."
    ),

    INACTIVE_USER(
        HttpStatus.UNAUTHORIZED,
        "inactive_user",
        "비활성화된 사용자입니다."
    ),

    SOCIAL_UNLINK_IN_PROGRESS(
        HttpStatus.CONFLICT,
        "social_unlink_in_progress",
        "소셜 계정 연동 해제가 진행 중입니다."
    ),

    UNSUPPORTED_PROVIDER(
        HttpStatus.BAD_REQUEST,
        "unsupported_provider",
        "지원하지 않는 소셜 로그인 제공자입니다."
    ),

    INVALID_OAUTH_RESPONSE(
        HttpStatus.UNAUTHORIZED,
        "invalid_oauth_response",
        "소셜 로그인 사용자 정보가 올바르지 않습니다."
    ),

    AUTHORIZATION_REQUEST_NOT_FOUND(
        HttpStatus.UNAUTHORIZED,
        "authorization_request_not_found",
        "OAuth 로그인 요청 정보를 찾을 수 없습니다."
    ),

    ALREADY_LINKED(
        HttpStatus.CONFLICT,
        "already_linked",
        "이미 다른 사용자에게 연결된 소셜 계정입니다."
    ),

    OAUTH2_ERROR(
        HttpStatus.UNAUTHORIZED,
        "oauth2_error",
        "소셜 로그인에 실패했습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    OAuth2LoginErrorCode(
        HttpStatus httpStatus,
        String code,
        String message
    ) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public static OAuth2LoginErrorCode from(
        String errorCode
    ) {
        return Arrays.stream(values())
            .filter(value ->
                value.code.equals(errorCode)
            )
            .findFirst()
            .orElse(OAUTH2_ERROR);
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
