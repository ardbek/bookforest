package com.ebook.bookforest.global.security.jwt.model;

import com.ebook.bookforest.domain.user.enums.UserRole;
import java.security.Principal;
import java.util.Objects;

/**
 * Access Token으로 인증된 사용자, Controller에서 @AuthenticationPrincipal로 받음
 */
public record JwtPrincipal(
    Long userId,
    UserRole role
) implements Principal {

    public JwtPrincipal {
        Objects.requireNonNull(userId, "userId는 null일 수 없습니다.");
        Objects.requireNonNull(role, "role은 null일 수 없습니다.");
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
