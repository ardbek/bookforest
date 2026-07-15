package com.ebook.bookforest.domain.auth.dto;

import com.ebook.bookforest.domain.user.entity.User;

/**
 * 필요한 값만 복사하여 프론트에 전달
 * @param userId
 * @param displayName
 * @param profileImageUrl
 */
public record AuthUserResponse(Long userId, String displayName, String profileImageUrl) {

    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(user.getId(), user.getDisplayName(), user.getProfileImageUrl());
    }

}
