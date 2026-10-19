package com.ebook.bookforest.domain.user.exception;

import com.ebook.bookforest.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 사용자 도메인 오류 코드
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "user_not_found", "사용자를 찾을 수 없습니다."),
    USER_ALREADY_WITHDRAWN(HttpStatus.CONFLICT, "user_already_withdrawn", "이미 탈퇴한 사용자입니다."),
    /** 소셜 로그인 실패 사유로 프론트 콜백 URL에 전달되므로 OAuth2LoginErrorCode와 같은 코드 문자열 사용 */
    SOCIAL_UNLINK_IN_PROGRESS(HttpStatus.CONFLICT, "social_unlink_in_progress", "소셜 계정 연동 해제가 진행 중입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
