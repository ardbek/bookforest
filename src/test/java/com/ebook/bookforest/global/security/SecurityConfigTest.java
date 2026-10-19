package com.ebook.bookforest.global.security;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebook.bookforest.domain.searchkeyword.config.SearchKeywordWebConfig;
import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.domain.user.repository.UserRepository;
import com.ebook.bookforest.domain.user.service.UserService;
import com.ebook.bookforest.global.exception.ErrorResponseWriter;
import com.ebook.bookforest.global.security.jwt.JwtAccessDeniedHandler;
import com.ebook.bookforest.global.security.jwt.JwtAuthenticationEntryPoint;
import com.ebook.bookforest.global.security.jwt.TokenProvider;
import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
import com.ebook.bookforest.global.security.jwt.model.ParsedAccessToken;
import com.ebook.bookforest.global.security.oauth.CustomOAuth2UserService;
import com.ebook.bookforest.global.security.oauth.OAuth2AuthenticationFailureHandler;
import com.ebook.bookforest.global.security.oauth.OAuth2AuthenticationSuccessHandler;
import io.jsonwebtoken.ExpiredJwtException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보안 필터 체인 검증
 * 실제 SecurityConfig와 JWT 필터를 적용하고, 경로마다 응답만 돌려주는 테스트 Controller로 인증 허용 범위와 오류 응답 확인
 */
@WebMvcTest(
    controllers = SecurityConfigTest.PathController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SearchKeywordWebConfig.class)
)
@Import({
    SecurityConfig.class,
    JwtAuthenticationEntryPoint.class,
    JwtAccessDeniedHandler.class,
    ErrorResponseWriter.class,
    UserService.class,
    SecurityConfigTest.OAuth2ClientTestConfig.class,
    SecurityConfigTest.PathController.class
})
class SecurityConfigTest {

    private static final String VALID_TOKEN = "valid-access-token";
    private static final String EXPIRED_TOKEN = "expired-access-token";
    private static final Long USER_ID = 1L;
    private static final String PROTECTED_PATH = "/api/users/me/favorite-books";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenProvider tokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2AuthenticationSuccessHandler successHandler;

    @MockitoBean
    private OAuth2AuthenticationFailureHandler failureHandler;

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/platform",
        "/api/library",
        "/api/book/search",
        "/api/book/1",
        "/api/categories/standard",
        "/api/books/popular",
        "/api/books/trending",
        "/api/search-keywords/popular"
    })
    @DisplayName("비로그인 허용 GET 경로는 토큰 없이 통과")
    void publicGetPathsPermitWithoutToken(String path) throws Exception {
        mockMvc.perform(get(path))
            .andExpect(status().isOk())
            .andExpect(content().string("ok"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/reissue", "/api/logout"})
    @DisplayName("재발급, 로그아웃은 잘못된 Authorization 헤더가 있어도 JWT 검사 없이 통과")
    void reissueAndLogoutSkipJwtFilter(String path) throws Exception {
        // 필터가 servletPath로 경로를 비교하므로, DispatcherServlet이 "/"에 매핑된 실제 서버처럼 지정
        mockMvc.perform(post(path).servletPath(path).header(HttpHeaders.AUTHORIZATION, "Basic invalid"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET만 허용한 랭킹 경로에 다른 메서드로 토큰 없이 요청하면 401")
    void rankingPathPermitsGetOnly() throws Exception {
        mockMvc.perform(post("/api/books/popular"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("authentication_required"));
    }

    @Test
    @DisplayName("로그인 필요 경로에 토큰 없이 요청하면 401 authentication_required")
    void protectedPathRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me/favorite-books"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("authentication_required"))
            .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    @Test
    @DisplayName("활성 사용자의 유효한 토큰이면 로그인 필요 경로 통과, 사용자 ID 전달")
    void validTokenOfActiveUserAuthenticates() throws Exception {
        // given
        givenValidToken();
        given(userRepository.existsByIdAndActiveTrue(USER_ID)).willReturn(true);

        // when, then
        mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(content().string("user-1"));
    }

    @Test
    @DisplayName("비활성 사용자의 토큰이면 401 user_not_active")
    void tokenOfInactiveUserIsRejected() throws Exception {
        // given
        givenValidToken();
        given(userRepository.existsByIdAndActiveTrue(USER_ID)).willReturn(false);

        // when, then
        mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("user_not_active"));
    }

    @Test
    @DisplayName("만료된 토큰이면 401 expired_access_token")
    void expiredTokenIsRejected() throws Exception {
        // given
        given(tokenProvider.parseAccessToken(EXPIRED_TOKEN))
            .willThrow(new ExpiredJwtException(null, null, "expired"));

        // when, then
        mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + EXPIRED_TOKEN))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("expired_access_token"));
    }

    @Test
    @DisplayName("Bearer 형식이 아닌 Authorization 헤더는 비로그인 허용 경로에서도 401 invalid_access_token")
    void malformedHeaderIsRejectedEvenOnPublicPath() throws Exception {
        mockMvc.perform(get("/api/platform").header(HttpHeaders.AUTHORIZATION, "Basic invalid"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("invalid_access_token"));
    }

    @Test
    @DisplayName("허용한 출처의 사전 요청은 인증 없이 CORS 허용, Retry-After 헤더 노출")
    void preflightFromAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/users/me/favorite-books")
                .header(HttpHeaders.ORIGIN, "https://bookforest.cc")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://bookforest.cc"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(get("/api/platform").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Retry-After"));
    }

    @Test
    @DisplayName("허용하지 않은 출처의 사전 요청은 403")
    void preflightFromUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/platform")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
            .andExpect(status().isForbidden());
    }

    private void givenValidToken() {
        given(tokenProvider.parseAccessToken(VALID_TOKEN)).willReturn(new ParsedAccessToken(
            USER_ID,
            "token-id",
            UserRole.ROLE_USER,
            Instant.now().plusSeconds(60)
        ));
    }

    @RestController
    static class PathController {

        @GetMapping({
            "/api/platform",
            "/api/library",
            "/api/book/search",
            "/api/book/1",
            "/api/categories/standard",
            "/api/books/popular",
            "/api/books/trending",
            "/api/search-keywords/popular"
        })
        String publicPath() {
            return "ok";
        }

        @PostMapping({"/api/reissue", "/api/logout"})
        String authPath() {
            return "ok";
        }

        @GetMapping("/api/users/me/favorite-books")
        String protectedPath(@AuthenticationPrincipal JwtPrincipal principal) {
            return "user-" + principal.userId();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OAuth2ClientTestConfig {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("kakao")
                .clientId("test-client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://auth.example/authorize")
                .tokenUri("https://auth.example/token")
                .build());
        }
    }
}
