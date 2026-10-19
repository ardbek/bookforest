package com.ebook.bookforest.global.security.oauth;

import com.ebook.bookforest.domain.user.enums.SocialProvider;

/**
 * 소셜 플랫폼별 사용자 정보 응답을 같은 형태로 읽기 위한 인터페이스
 * 플랫폼이 주지 않은 값은 null
 */
public interface OAuth2Response {

    SocialProvider getProvider();

    /** 소셜 플랫폼이 부여한 사용자 식별값 */
    String getProviderSubject();

    String getEmail();

    String getNickname();

    String getProfileImageUrl();
}
