package com.ebook.bookforest.global.security.jwt.model;

import com.ebook.bookforest.domain.user.enums.UserRole;
import java.time.Instant;

public record ParsedAccessToken(
    Long userId,
    String tokenId,
    UserRole role,
    Instant expiresAt
) {
}
