package com.ebook.bookforest.domain.searchkeyword.config;

import com.ebook.bookforest.domain.searchkeyword.service.SearchKeywordRules;
import com.ebook.bookforest.domain.searchkeyword.service.SearchKeywordService;
import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 검색 API 요청 전처리
 *
 * <p>처리 순서
 * <ul>
 *   <li>요청한 사용자 식별값을 해시해 요청 속성에 저장, 익명 사용자에게는 식별 쿠키 발급</li>
 *   <li>사용자별, IP별 요청 한도를 넘으면 429와 Retry-After 응답</li>
 *   <li>검색어가 너무 길거나 정규화 후 2~100자 규칙에 맞지 않으면 400 응답</li>
 * </ul>
 */
public class SearchRequestInterceptor implements HandlerInterceptor {

    /** Controller가 검색어를 기록할 때 쓰는 사용자 식별값 해시의 요청 속성 이름 */
    public static final String ACTOR_ATTRIBUTE = SearchRequestInterceptor.class.getName() + ".actor";

    private static final String ANONYMOUS_COOKIE = "bf_anon_id";
    private static final Duration ANONYMOUS_COOKIE_MAX_AGE = Duration.ofDays(180);
    private static final int MAX_RAW_KEYWORD_LENGTH = 1000;
    private static final int MAX_FORWARDED_HEADER_LENGTH = 2048;
    private static final Pattern IP_ADDRESS = Pattern.compile("[0-9a-fA-F:.]{3,45}");
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";
    private static final String TOO_MANY_REQUESTS_BODY =
        "{\"message\":\"검색 요청이 많습니다. 잠시 후 다시 시도해 주세요.\"}";
    private static final String INVALID_KEYWORD_BODY =
        "{\"message\":\"검색어는 문자 또는 숫자를 포함해 2~100글자로 입력해 주세요.\"}";

    private final SearchKeywordService searchKeywordService;
    private final SearchKeywordProperties properties;

    public SearchRequestInterceptor(SearchKeywordService searchKeywordService, SearchKeywordProperties properties) {
        this.searchKeywordService = searchKeywordService;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws IOException {
        if (!"GET".equals(request.getMethod())) {
            return true;
        }

        String actor = resolveActor(request, response);
        request.setAttribute(ACTOR_ATTRIBUTE, actor);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        long retryAfterSeconds = searchKeywordService.getRetryAfterSeconds(actor, resolveClientIp(request));
        if (retryAfterSeconds > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
            writeJson(response, HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_REQUESTS_BODY);
            return false;
        }

        String keyword = request.getParameter("keyword");
        if (keyword != null && !isAcceptableKeyword(keyword)) {
            writeJson(response, HttpStatus.BAD_REQUEST, INVALID_KEYWORD_BODY);
            return false;
        }
        return true;
    }

    /** 요청한 사용자 식별값 해시, 로그인 사용자는 사용자 ID, 익명 사용자는 식별 쿠키 기준 */
    String resolveActor(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal principal) {
            return searchKeywordService.hashActor("user:" + principal.userId());
        }

        String anonymousId = readAnonymousId(request);
        if (!SearchKeywordService.isUuid(anonymousId)) {
            anonymousId = UUID.randomUUID().toString();
            response.addHeader(HttpHeaders.SET_COOKIE, anonymousCookie(anonymousId));
        }
        return searchKeywordService.hashActor("anonymous:" + anonymousId.toLowerCase(Locale.ROOT));
    }

    /**
     * 요청 한도에 쓸 클라이언트 IP
     * ForwardedHeaderFilter가 바꾸기 전의 실제 접속 주소를 기준으로, 설정한 프록시를 거친 경우만 X-Forwarded-For를 신뢰
     */
    String resolveClientIp(HttpServletRequest request) {
        ServletRequest raw = request;
        while (raw instanceof ServletRequestWrapper wrapper) {
            raw = wrapper.getRequest();
        }

        String peer = raw.getRemoteAddr();
        if (!(raw instanceof HttpServletRequest http) || !properties.getTrustedProxies().contains(peer)) {
            return peer;
        }

        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.length() > MAX_FORWARDED_HEADER_LENGTH) {
            return peer;
        }

        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0 && properties.getTrustedProxies().contains(peer); i--) {
            String hop = hops[i].trim();
            if (!IP_ADDRESS.matcher(hop).matches()) {
                break;
            }
            peer = hop;
        }
        return peer;
    }

    private boolean isAcceptableKeyword(String keyword) {
        return keyword.length() <= MAX_RAW_KEYWORD_LENGTH
            && SearchKeywordRules.isValid(SearchKeywordRules.normalize(keyword));
    }

    private String readAnonymousId(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
            .filter(cookie -> cookie.getName().equals(ANONYMOUS_COOKIE))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }

    private String anonymousCookie(String anonymousId) {
        return ResponseCookie.from(ANONYMOUS_COOKIE, anonymousId)
            .path("/")
            .maxAge(ANONYMOUS_COOKIE_MAX_AGE)
            .httpOnly(true)
            .secure(properties.isCookieSecure())
            .sameSite("Lax")
            .build()
            .toString();
    }

    private void writeJson(HttpServletResponse response, HttpStatus status, String body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(JSON_CONTENT_TYPE);
        response.getWriter().write(body);
    }
}
