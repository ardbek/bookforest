package com.ebook.bookforest.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ebook.bookforest.domain.auth.exception.AuthErrorCode;
import com.ebook.bookforest.domain.auth.exception.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.json.JsonMapper;

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

    @Test
    @DisplayName("입력 검증 실패는 400 invalid_request와 필드별 사유 errors 배열로 응답")
    void validationFailureReturnsFieldErrors() throws Exception {
        // given
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "keyword", "2자 이상 입력해야 합니다."));
        MethodParameter parameter = new MethodParameter(
            GlobalExceptionHandlerTest.class.getDeclaredMethod("validationTarget", String.class),
            0
        );

        // when
        ResponseEntity<ErrorResponse> result =
            handler.handleValidationFailure(new MethodArgumentNotValidException(parameter, bindingResult));

        // then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody().code()).isEqualTo("invalid_request");
        assertThat(result.getBody().errors())
            .containsExactly(new ErrorResponse.FieldErrorResponse("keyword", "2자 이상 입력해야 합니다."));
        assertThat(JsonMapper.builder().build().writeValueAsString(result.getBody()))
            .isEqualTo("{\"code\":\"invalid_request\",\"message\":\"요청 값이 올바르지 않습니다.\","
                + "\"errors\":[{\"field\":\"keyword\",\"reason\":\"2자 이상 입력해야 합니다.\"}]}");
    }

    @Test
    @DisplayName("검증 실패가 아닌 오류 응답 JSON에는 errors 필드 없음")
    void errorResponseOmitsEmptyErrors() {
        // when
        String json = JsonMapper.builder().build()
            .writeValueAsString(ErrorResponse.from(CommonErrorCode.INVALID_REQUEST));

        // then
        assertThat(json).isEqualTo("{\"code\":\"invalid_request\",\"message\":\"요청 값이 올바르지 않습니다.\"}");
    }

    @SuppressWarnings("unused")
    private void validationTarget(String request) {
    }
}
