package com.ebook.bookforest.domain.auth.model;

import java.time.Instant;

/**
 * Refresh Token 교체 요청
 * 제출된 토큰이 현재 토큰이나 유예 기간 안의 직전 토큰과 일치할 때만 새 토큰으로 교체
 */
public record RefreshTokenRotationCommand(
    Long userId,
    String sessionId,
    String presentedTokenId,
    String presentedTokenHash,
    String newTokenId,
    String newTokenHash,
    /** 교체 후 직전 토큰을 계속 허용하는 마감 시각 */
    Instant previousValidUntil,
    Instant newExpiresAt
) {
}
