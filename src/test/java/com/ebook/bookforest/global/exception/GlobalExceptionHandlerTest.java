package com.ebook.bookforest.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ebook.bookforest.domain.auth.exception.AuthErrorCode;
import com.ebook.bookforest.domain.auth.exception.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void 비즈니스_예외를_ErrorCode의_상태와_공통_응답으로_변환한다() {
        ResponseEntity<ErrorResponse> result =
            handler.handleBusinessException(
                new AuthException(
                    AuthErrorCode.INVALID_REFRESH_TOKEN
                )
            );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(result.getBody()).isEqualTo(ErrorResponse.from(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void 읽을_수_없는_요청은_INVALID_REQUEST로_변환한다() {
        ResponseEntity<ErrorResponse> result =
            handler.handleInvalidRequest(
                mock(HttpMessageNotReadableException.class)
            );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isEqualTo(ErrorResponse.from(CommonErrorCode.INVALID_REQUEST));
    }

    @Test
    void 지원하지_않는_HTTP_메서드는_METHOD_NOT_ALLOWED로_변환한다() {
        ResponseEntity<ErrorResponse> result =
            handler.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException(
                    "TRACE"
                )
            );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(result.getBody()).isEqualTo(ErrorResponse.from(CommonErrorCode.METHOD_NOT_ALLOWED));
    }

    @Test
    void 존재하지_않는_리소스는_RESOURCE_NOT_FOUND로_변환한다() {
        ResponseEntity<ErrorResponse> result =
            handler.handleNotFound(
                mock(NoResourceFoundException.class)
            );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isEqualTo(ErrorResponse.from(CommonErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void 예상하지_못한_예외의_내부_메시지는_응답에_노출하지_않는다() {
        String sensitiveMessage =
            "SQL 및 서버 내부 정보";

        ResponseEntity<ErrorResponse> result =
            handler.handleUnexpectedException(
                new RuntimeException(sensitiveMessage)
            );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody()).isEqualTo(ErrorResponse.from(CommonErrorCode.INTERNAL_SERVER_ERROR));
        assertThat(result.getBody().message()).doesNotContain(sensitiveMessage);
    }
}
