package com.ebook.bookforest.domain.searchkeyword;

import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class SearchKeywordWebConfig implements WebMvcConfigurer {
    public static final String ACTOR = SearchKeywordWebConfig.class.getName() + ".actor";
    private final SearchKeywordService rankings;
    private final SearchKeywordProperties config;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                if (!"GET".equals(request.getMethod())) return true;
                String actor = resolveActor(request, response);
                request.setAttribute(ACTOR, actor);
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                long retry = rankings.retryAfter(actor, clientIp(request));
                if (retry > 0) {
                    response.setStatus(429);
                    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retry));
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"검색 요청이 많습니다. 잠시 후 다시 시도해 주세요.\"}");
                    return false;
                }
                String keyword = request.getParameter("keyword");
                if (keyword != null && (keyword.length() > 1000 || !SearchKeywordRules.valid(SearchKeywordRules.normalize(keyword)))) {
                    response.setStatus(400);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"검색어는 문자 또는 숫자를 포함해 2~100글자로 입력해 주세요.\"}");
                    return false;
                }
                return true;
            }
        }).addPathPatterns("/api/book/search", "/api/book/search/platforms");
    }

    String resolveActor(HttpServletRequest request, HttpServletResponse response) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal principal) {
            return rankings.actorHash("user:" + principal.userId());
        }
        String anonymous = request.getCookies() == null ? null : Arrays.stream(request.getCookies())
            .filter(cookie -> cookie.getName().equals("bf_anon_id")).map(cookie -> cookie.getValue()).findFirst().orElse(null);
        if (!SearchKeywordService.validEvent(anonymous)) {
            anonymous = UUID.randomUUID().toString();
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("bf_anon_id", anonymous)
                .path("/").maxAge(Duration.ofDays(180)).httpOnly(true).secure(config.isCookieSecure()).sameSite("Lax").build().toString());
        }
        return rankings.actorHash("anonymous:" + anonymous.toLowerCase(java.util.Locale.ROOT));
    }

    String clientIp(HttpServletRequest request) {
        // ForwardedHeaderFilter 이전의 실제 peer를 확인한다. 명시한 프록시만 신뢰한다.
        ServletRequest raw = request;
        while (raw instanceof ServletRequestWrapper wrapper) raw = wrapper.getRequest();
        String peer = raw.getRemoteAddr();
        if (!(raw instanceof HttpServletRequest http) || !config.getTrustedProxies().contains(peer)) return peer;
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.length() > 2048) return peer;
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0 && config.getTrustedProxies().contains(peer); i--) {
            String hop = hops[i].trim();
            if (!hop.matches("[0-9a-fA-F:.]{3,45}")) break;
            peer = hop;
        }
        return peer;
    }
}
