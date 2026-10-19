package com.ebook.bookforest.domain.user.dto;

import com.ebook.bookforest.domain.user.entity.User;
import com.ebook.bookforest.domain.user.enums.UserRole;

/**
 * 다른 도메인에 제공하는 사용자 조회 정보
 */
public record UserInfo(
    Long id,
    UserRole role,
    String displayName,
    String profileImageUrl
) {

    public static UserInfo from(User user) {
        return new UserInfo(
            user.getId(),
            user.getRole(),
            user.getDisplayName(),
            user.getProfileImageUrl()
        );
    }
}
