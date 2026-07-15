package com.ebook.bookforest.global.security.oauth;

import com.ebook.bookforest.domain.user.enums.SocialProvider;

public interface OAuth2Response {

    /* 소셜 로그인 플랫폼 */
    SocialProvider getProvider();

    /* 사용자 식별값 */
    String getProviderSubject();

    /* 이메일 */
    String getEmail();

    /* 닉네임 */
    String getNickname();

    /* 프로필 이미지 */
    String getProfileImageUrl();
}
