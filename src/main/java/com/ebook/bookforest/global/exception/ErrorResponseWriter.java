package com.ebook.bookforest.global.exception;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {
    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {

        if (response.isCommitted()) {
            return;
        }

        response.resetBuffer();

        response.setStatus(errorCode.httpStatus().value());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        objectMapper.writeValue(
            response.getOutputStream(),
            ErrorResponse.from(errorCode)
        );

        response.flushBuffer();
    }
}
