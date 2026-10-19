package com.ebook.bookforest.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.ebook.bookforest.domain.auth.exception.AuthErrorCode;
import com.ebook.bookforest.domain.auth.exception.AuthException;
import com.ebook.bookforest.global.exception.ErrorResponseWriter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class JwtExceptionHandlerFilterTest {

    private JwtExceptionHandlerFilter filter;

    @BeforeEach
    void setUp() {
        ErrorResponseWriter writer = new ErrorResponseWriter(
            JsonMapper.builder().build()
        );
        filter = new JwtExceptionHandlerFilter(writer);
    }

    @Test
    void 만료된_JWT_예외를_expired_access_token_응답으로_변환한다()
        throws Exception {

        FilterChain chain = mock(FilterChain.class);
        willThrow(new ExpiredJwtException(
            mock(Header.class),
            mock(Claims.class),
            "expired"
        )).given(chain).doFilter(any(), any());

        MockHttpServletResponse response =
            execute(chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).isEqualTo("{\"code\":\"expired_access_token\","
                + "\"message\":\"Access Token이 만료되었습니다.\"}");
    }

    @Test
    void 잘못된_JWT_예외를_invalid_access_token_응답으로_변환한다()
        throws Exception {

        FilterChain chain = mock(FilterChain.class);
        willThrow(new MalformedJwtException("invalid"))
            .given(chain).doFilter(any(), any());

        MockHttpServletResponse response =
            execute(chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).isEqualTo("{\"code\":\"invalid_access_token\","
                + "\"message\":\"Access Token이 올바르지 않습니다.\"}");
    }

    @Test
    void 비활성_사용자_예외를_user_not_active_응답으로_변환한다()
        throws Exception {

        FilterChain chain = mock(FilterChain.class);
        willThrow(new AuthException(AuthErrorCode.USER_NOT_ACTIVE))
            .given(chain).doFilter(any(), any());

        MockHttpServletResponse response = execute(chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).isEqualTo("{\"code\":\"user_not_active\","
                + "\"message\":\"활성화된 사용자를 찾을 수 없습니다.\"}");
    }

    @Test
    void JWT와_무관한_IllegalArgumentException은_가로채지_않는다()
        throws Exception {
        FilterChain chain = mock(FilterChain.class);
        willThrow(new IllegalArgumentException("not a jwt error"))
            .given(chain).doFilter(any(), any());

        assertThatThrownBy(() -> filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                chain
            )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 예외가_없으면_다음_Filter를_호출한다()
        throws Exception {

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request =
            new MockHttpServletRequest();
        MockHttpServletResponse response =
            new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        then(chain).should().doFilter(request, response);
    }

    private MockHttpServletResponse execute(
        FilterChain chain
    ) throws Exception {
        MockHttpServletResponse response =
            new MockHttpServletResponse();

        filter.doFilter(
            new MockHttpServletRequest(),
            response,
            chain
        );

        return response;
    }
}
