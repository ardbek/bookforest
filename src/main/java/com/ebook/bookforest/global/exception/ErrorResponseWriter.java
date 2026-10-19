package com.ebook.bookforest.global.exception;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 공통 오류 응답 작성기(보안 필터 영역용)
 * MVC 밖이라 GlobalExceptionHandler가 처리하지 못하는 인증, 인가 오류를 같은 응답 형식으로 작성
 */
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    /** 오류 응답 작성, 이미 커밋된 응답은 덮어쓰지 않음 */
    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.resetBuffer();
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.from(errorCode));
        response.flushBuffer();
    }
}
