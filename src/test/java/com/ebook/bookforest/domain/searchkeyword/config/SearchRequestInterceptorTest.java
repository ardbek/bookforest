package com.ebook.bookforest.domain.searchkeyword.config;

import com.ebook.bookforest.domain.searchkeyword.service.SearchKeywordService;
import com.ebook.bookforest.domain.searchkeyword.service.SearchKeywordServiceFixture;
import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.ForwardedHeaderFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 검색 요청 전처리의 사용자 식별과 클라이언트 IP 판단 검증, Redis 불필요
 */
class SearchRequestInterceptorTest {
    SearchKeywordProperties config;
    SearchKeywordService service;
    SearchRequestInterceptor interceptor;

    @BeforeEach
    void setUp() {
        config = new SearchKeywordProperties();
        config.setActorSecret("test-only-secret");
        service = SearchKeywordServiceFixture.create(
            config, mock(StringRedisTemplate.class), Clock.fixed(Instant.now(), ZoneOffset.UTC)
        );
        interceptor = new SearchRequestInterceptor(service, config);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test
    void actorCookiesAndAuthenticatedUsersAreResolvedServerSide() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        String anonymous = interceptor.resolveActor(request, response);
        assertThat(anonymous).doesNotContain("anonymous");
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly");
        assertThat(response.getHeader("Set-Cookie")).contains("Secure");
        String cookieValue = response.getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1];
        request.setCookies(new jakarta.servlet.http.Cookie("bf_anon_id", cookieValue));
        assertThat(interceptor.resolveActor(request, new MockHttpServletResponse())).isEqualTo(anonymous);
        var principal = new JwtPrincipal(42L, UserRole.values()[0]);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        assertThat(interceptor.resolveActor(request, response)).isEqualTo(service.hashActor("user:42"));
    }

    @Test
    void forwardedHeadersOnlyAffectLimitsForConfiguredProxyPeers() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/book/search/platforms");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.2");
        assertThat(interceptor.resolveClientIp(request)).isEqualTo("10.0.0.1");
        config.setTrustedProxies(List.of("10.0.0.1", "10.0.0.2"));
        new ForwardedHeaderFilter().doFilter(request, new MockHttpServletResponse(), (wrapped, response) ->
            assertThat(interceptor.resolveClientIp((jakarta.servlet.http.HttpServletRequest) wrapped))
                .isEqualTo("1.2.3.4"));
    }
}
