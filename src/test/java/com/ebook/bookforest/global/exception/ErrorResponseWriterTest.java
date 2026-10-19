package com.ebook.bookforest.global.exception;


import static org.assertj.core.api.Assertions.assertThat;

import com.ebook.bookforest.global.security.SecurityErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class ErrorResponseWriterTest {

    private final ErrorResponseWriter writer =
        new ErrorResponseWriter(JsonMapper.builder().build());

    @Test
    void ErrorCode를_HTTP_상태와_공통_JSON_응답으로_작성한다()
        throws Exception {

        MockHttpServletResponse response =
            new MockHttpServletResponse();

        writer.write(
            response,
            SecurityErrorCode.INVALID_ACCESS_TOKEN
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(response.getContentAsString()).isEqualTo("{\"code\":\"invalid_access_token\","
                + "\"message\":\"Access Token이 올바르지 않습니다.\"}");
        assertThat(response.isCommitted()).isTrue();
    }

    @Test
    void 이미_commit된_응답은_덮어쓰지_않는다()
        throws Exception {

        MockHttpServletResponse response =
            new MockHttpServletResponse();
        response.setStatus(202);
        response.getWriter().write("already written");
        response.flushBuffer();

        writer.write(
            response,
            SecurityErrorCode.ACCESS_DENIED
        );

        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(response.getContentAsString()).isEqualTo("already written");
    }
}
