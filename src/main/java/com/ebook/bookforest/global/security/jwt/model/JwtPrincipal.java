package com.ebook.bookforest.global.security.jwt.model;

import com.ebook.bookforest.domain.user.enums.UserRole;
import java.security.Principal;
import java.util.Objects;

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
