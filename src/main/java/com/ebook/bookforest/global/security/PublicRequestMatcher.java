package com.ebook.bookforest.global.security;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 비로그인 허용 요청
 * SecurityConfig의 허용 범위와 JwtAuthenticationFilter의 토큰 오류 무시 범위가 같도록 한 곳에서 관리
 */
public final class PublicRequestMatcher implements RequestMatcher {

    private static final List<String> INFRA_PATHS = List.of("/actuator/health", "/error");
    private static final List<String> LOGIN_PATHS = List.of(
        "/oauth2/**",
        "/login/oauth2/**",
        "/api/reissue",
        "/api/logout"
    );
    private static final List<String> PUBLIC_GET_PATHS = List.of(
        "/api/books/popular",
        "/api/books/trending",
        "/api/search-keywords/popular"
    );
    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/platform",
        "/api/platform/**",
        "/api/library",
        "/api/library/**",
        "/api/book",
        "/api/book/**",
        "/api/categories",
        "/api/categories/**"
    );

    private final RequestMatcher delegate = new OrRequestMatcher(createMatchers());

    @Override
    public boolean matches(HttpServletRequest request) {
        return delegate.matches(request);
    }

    private static List<RequestMatcher> createMatchers() {
        List<RequestMatcher> matchers = new ArrayList<>();
        INFRA_PATHS.forEach(path -> matchers.add(pathPattern(path)));
        LOGIN_PATHS.forEach(path -> matchers.add(pathPattern(path)));
        matchers.add(pathPattern(HttpMethod.OPTIONS, "/**"));
        PUBLIC_GET_PATHS.forEach(path -> matchers.add(pathPattern(HttpMethod.GET, path)));
        PUBLIC_PATHS.forEach(path -> matchers.add(pathPattern(path)));
        return matchers;
    }
}
