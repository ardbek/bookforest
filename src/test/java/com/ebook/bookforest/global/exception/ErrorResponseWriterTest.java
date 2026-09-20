package com.ebook.bookforest.global.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertEquals(
            "{\"code\":\"invalid_access_token\","
                + "\"message\":\"Access Token이 올바르지 않습니다.\"}",
            response.getContentAsString()
        );
        assertTrue(response.isCommitted());
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

        assertEquals(202, response.getStatus());
        assertEquals("already written", response.getContentAsString());
    }
}
