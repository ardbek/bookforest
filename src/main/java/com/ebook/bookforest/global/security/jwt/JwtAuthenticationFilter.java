package com.ebook.bookforest.global.security.jwt;

import com.ebook.bookforest.domain.auth.exception.AuthErrorCode;
import com.ebook.bookforest.domain.auth.exception.AuthException;
import com.ebook.bookforest.domain.user.service.UserService;
import com.ebook.bookforest.global.security.PublicRequestMatcher;
import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
import com.ebook.bookforest.global.security.jwt.model.ParsedAccessToken;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization 헤더의 Access Token 인증 필터
 *
 * <p>토큰 오류(형식, 서명, 만료, 비활성 사용자) 처리
 * <ul>
 *   <li>비로그인 허용 경로: 오류를 무시하고 비로그인 요청으로 통과</li>
 *   <li>그 외 경로: 예외를 던지고 앞단의 JwtExceptionHandlerFilter가 오류 응답으로 변환</li>
 * </ul>
 * 프론트가 로그인 상태에서 공개 API에도 토큰을 붙이므로, 토큰이 만료돼도 공개 API가 실패하지 않도록 하기 위함
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final RequestMatcher PUBLIC_REQUEST_MATCHER = new PublicRequestMatcher();

    private final TokenProvider tokenProvider;
    private final UserService userService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (isAlreadyAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            authenticate(request);
        } catch (JwtException | AuthException exception) {
            if (!PUBLIC_REQUEST_MATCHER.matches(request)) {
                throw exception;
            }
            log.debug("비로그인 허용 경로의 토큰 오류 무시: exceptionType={}", exception.getClass().getSimpleName());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 필터 제외 경로(토큰 재발급, 로그아웃, 소셜 로그인)
     * 재발급 요청에는 만료된 Access Token이 함께 올 수 있어, 검사하면 재발급까지 도달하지 못하고 expired_access_token으로 실패
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        return path.equals("/api/reissue")
            || path.equals("/api/logout")
            || path.startsWith("/oauth2/")
            || path.startsWith("/login/oauth2/");
    }

    private void authenticate(HttpServletRequest request) {
        String accessToken = resolveAccessToken(request);
        if (accessToken == null) {
            return;
        }

        ParsedAccessToken parsedToken = tokenProvider.parseAccessToken(accessToken);
        validateActiveUser(parsedToken.userId());
        setAuthentication(request, parsedToken);
    }

    private boolean isAlreadyAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return null;
        }

        if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new MalformedJwtException("Authorization 헤더 형식이 올바르지 않습니다.");
        }

        String accessToken = authorization.substring(BEARER_PREFIX.length()).trim();
        if (accessToken.isBlank()) {
            throw new MalformedJwtException("Access Token이 비어 있습니다.");
        }

        return accessToken;
    }

    private void validateActiveUser(Long userId) {
        if (!userService.isActiveUser(userId)) {
            throw new AuthException(AuthErrorCode.USER_NOT_ACTIVE);
        }
    }

    private void setAuthentication(HttpServletRequest request, ParsedAccessToken parsedToken) {
        JwtPrincipal principal = new JwtPrincipal(parsedToken.userId(), parsedToken.role());
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(parsedToken.role().name());

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
