package com.ebook.bookforest.global.security.jwt.model;

import com.ebook.bookforest.domain.user.enums.UserRole;
import java.time.Instant;

/**
 * 검증을 통과한 Access Token 내용
 */
public record ParsedAccessToken(
    Long userId,
    String tokenId,
    UserRole role,
    Instant expiresAt
) {
}
