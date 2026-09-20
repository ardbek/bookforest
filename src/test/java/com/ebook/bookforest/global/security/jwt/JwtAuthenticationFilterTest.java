package com.ebook.bookforest.global.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ebook.bookforest.domain.auth.exception.AuthErrorCode;
import com.ebook.bookforest.domain.auth.exception.AuthException;
import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.domain.user.repository.UserRepository;
import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
import com.ebook.bookforest.global.security.jwt.model.ParsedAccessToken;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private TokenProvider tokenProvider;
    private UserRepository userRepository;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenProvider = mock(TokenProvider.class);
        userRepository = mock(UserRepository.class);
        filter = new JwtAuthenticationFilter(
            tokenProvider,
            userRepository
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void Authorization_헤더가_없으면_인증하지_않고_통과한다()
        throws Exception {

        MockHttpServletRequest request =
            new MockHttpServletRequest();
        MockHttpServletResponse response =
            new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(tokenProvider);
        assertEquals(
            null,
            SecurityContextHolder.getContext().getAuthentication()
        );
    }

    @Test
    void 정상_Access_Token이면_SecurityContext에_사용자와_권한을_저장한다()
        throws Exception {

        ParsedAccessToken parsedToken = new ParsedAccessToken(
            42L,
            "access-token-id",
            UserRole.ROLE_USER,
            Instant.now().plusSeconds(1800)
        );
        when(tokenProvider.parseAccessToken("valid-token"))
            .thenReturn(parsedToken);
        when(userRepository.existsByIdAndActiveTrue(42L))
            .thenReturn(true);

        MockHttpServletRequest request = bearerRequest("valid-token");
        MockHttpServletResponse response =
            new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder
            .getContext()
            .getAuthentication();
        assertTrue(authentication.isAuthenticated());
        assertEquals(
            new JwtPrincipal(42L, UserRole.ROLE_USER),
            authentication.getPrincipal()
        );
        assertEquals(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            List.copyOf(authentication.getAuthorities())
        );
        verify(tokenProvider).parseAccessToken("valid-token");
        verify(userRepository).existsByIdAndActiveTrue(42L);
        verify(chain).doFilter(request, response);
    }

    @Test
    void 탈퇴한_사용자의_기존_Access_Token은_거부한다() {
        ParsedAccessToken parsedToken = new ParsedAccessToken(
            42L,
            "access-token-id",
            UserRole.ROLE_USER,
            Instant.now().plusSeconds(1800)
        );
        when(tokenProvider.parseAccessToken("valid-token"))
            .thenReturn(parsedToken);
        when(userRepository.existsByIdAndActiveTrue(42L))
            .thenReturn(false);

        MockHttpServletRequest request = bearerRequest("valid-token");
        MockHttpServletResponse response =
            new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        AuthException exception = assertThrows(
            AuthException.class,
            () -> filter.doFilter(request, response, chain)
        );

        assertEquals(
            AuthErrorCode.USER_NOT_ACTIVE,
            exception.getErrorCode()
        );
        assertEquals(
            null,
            SecurityContextHolder.getContext().getAuthentication()
        );
        verify(tokenProvider).parseAccessToken("valid-token");
        verify(userRepository).existsByIdAndActiveTrue(42L);
        verifyNoInteractions(chain);
    }

    @Test
    void Bearer_형식이_아닌_Authorization_헤더는_거부한다() {
        MockHttpServletRequest request =
            new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic value");

        assertThrows(
            MalformedJwtException.class,
            () -> filter.doFilter(
                request,
                new MockHttpServletResponse(),
                mock(FilterChain.class)
            )
        );
        verifyNoInteractions(tokenProvider);
    }

    @Test
    void Bearer_뒤의_토큰이_비어_있으면_거부한다() {
        assertThrows(
            MalformedJwtException.class,
            () -> filter.doFilter(
                bearerRequest("   "),
                new MockHttpServletResponse(),
                mock(FilterChain.class)
            )
        );
        verifyNoInteractions(tokenProvider);
    }

    @Test
    void 기존_인증이_있으면_JWT_인증으로_덮어쓰지_않는다()
        throws Exception {

        Authentication existing =
            new UsernamePasswordAuthenticationToken(
                "existing-user",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
        SecurityContextHolder.getContext().setAuthentication(existing);

        MockHttpServletRequest request = bearerRequest("valid-token");
        MockHttpServletResponse response =
            new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertSame(
            existing,
            SecurityContextHolder.getContext().getAuthentication()
        );
        verifyNoInteractions(tokenProvider);
        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/reissue",
        "/api/logout",
        "/oauth2/authorization/kakao",
        "/login/oauth2/code/naver"
    })
    void 토큰과_무관한_인증_경로는_JWT_Filter에서_제외한다(
        String path
    ) throws Exception {

        MockHttpServletRequest request = bearerRequest("expired-token");
        request.setServletPath(path);
        request.setRequestURI(path);
        MockHttpServletResponse response =
            new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(tokenProvider);
        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request =
            new MockHttpServletRequest();
        request.addHeader(
            HttpHeaders.AUTHORIZATION,
            "Bearer " + token
        );
        return request;
    }
}
