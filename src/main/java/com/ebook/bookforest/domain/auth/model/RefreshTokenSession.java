package com.ebook.bookforest.domain.auth.model;

import java.time.Instant;

/**
 * 로그인 세션별 Refresh Token 저장 정보
 * 여러 탭의 동시 재발급을 허용하기 위해 직전 토큰을 유예 기간 동안 함께 보관
 */
public record RefreshTokenSession(
    String sessionId,
    Long userId,
    String currentTokenId,
    String currentTokenHash,
    String previousTokenId,
    String previousTokenHash,
    /** 직전 토큰을 허용하는 마감 시각, 최초 발급 세션은 null */
    Instant previousValidUntil,
    Instant expiresAt
) {

    /** 최초 로그인 세션 생성, 직전 토큰 정보 없음 */
    public static RefreshTokenSession initial(
        String sessionId,
        Long userId,
        String tokenId,
        String tokenHash,
        Instant expiresAt
    ) {
        return new RefreshTokenSession(sessionId, userId, tokenId, tokenHash, null, null, null, expiresAt);
    }
}
