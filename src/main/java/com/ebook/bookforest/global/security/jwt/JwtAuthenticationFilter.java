package com.ebook.bookforest.global.security.jwt;

import com.ebook.bookforest.domain.auth.exception.AuthErrorCode;
import com.ebook.bookforest.domain.auth.exception.AuthException;
import com.ebook.bookforest.domain.user.service.UserService;
import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
import com.ebook.bookforest.global.security.jwt.model.ParsedAccessToken;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization 헤더의 Access Token 인증 필터
 * 헤더가 없으면 인증 없이 통과시키고, 헤더가 있으면 비로그인 허용 경로에서도 토큰을 검증
 * 토큰 오류와 비활성 사용자 예외는 앞단의 JwtExceptionHandlerFilter가 오류 응답으로 변환
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

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

        String accessToken = resolveAccessToken(request);
        if (accessToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        ParsedAccessToken parsedToken = tokenProvider.parseAccessToken(accessToken);
        validateActiveUser(parsedToken.userId());
        setAuthentication(request, parsedToken);

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
