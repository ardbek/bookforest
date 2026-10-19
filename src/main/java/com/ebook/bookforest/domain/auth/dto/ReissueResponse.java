package com.ebook.bookforest.domain.auth.dto;

/**
 * 토큰 재발급 응답
 * Refresh Token은 응답 본문이 아닌 HttpOnly 쿠키로만 전달
 */
public record ReissueResponse(
    String accessToken,
    /** Access Token 남은 유효 시간(초) */
    long expiresIn,
    AuthUserResponse user
) {

    public static ReissueResponse from(ReissueResult result) {
        return new ReissueResponse(
            result.accessToken(),
            result.accessTokenExpiresIn(),
            result.user()
        );
    }
}
