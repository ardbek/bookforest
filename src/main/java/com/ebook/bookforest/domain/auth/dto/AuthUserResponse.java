package com.ebook.bookforest.domain.auth.dto;

import com.ebook.bookforest.domain.user.dto.UserInfo;

/**
 * 재발급 응답에 담는 로그인 사용자 정보
 * 프론트가 JWT를 직접 해석하지 않도록 화면 표시에 필요한 값만 전달
 */
public record AuthUserResponse(
    Long userId,
    String displayName,
    String profileImageUrl
) {

    public static AuthUserResponse from(UserInfo user) {
        return new AuthUserResponse(user.id(), user.displayName(), user.profileImageUrl());
    }
}
