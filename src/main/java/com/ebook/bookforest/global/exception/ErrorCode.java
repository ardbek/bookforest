package com.ebook.bookforest.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류 응답에 쓰는 오류 코드
 * 도메인별 XxxErrorCode enum이 구현
 */
public interface ErrorCode {

    HttpStatus httpStatus();

    /** 프론트가 오류를 구분하는 lower_snake_case 코드 */
    String code();

    /** 사용자에게 보여도 되는 메시지 */
    String message();
}
