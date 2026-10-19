package com.ebook.bookforest.global.security.oauth;

import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.enums.UserRole;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * 소셜 로그인에 성공한 사용자, 로그인 성공 처리기가 토큰 발급과 소셜 토큰 저장에 사용
 */
@Getter
public class CustomOAuth2User implements OAuth2User {

    private final Long userId;
    private final UserRole role;
    private final SocialProvider provider;

    /** 소셜 플랫폼이 부여한 사용자 식별값 */
    private final String providerSubject;

    /** 소셜 플랫폼이 준 사용자 정보 원본, 수정 불가 */
    private final Map<String, Object> attributes;

    private final Collection<? extends GrantedAuthority> authorities;

    public CustomOAuth2User(
        Long userId,
        UserRole role,
        SocialProvider provider,
        String providerSubject,
        Map<String, Object> attributes
    ) {
        this.userId = Objects.requireNonNull(userId, "userId is null");
        this.role = Objects.requireNonNull(role, "role is null");
        this.provider = Objects.requireNonNull(provider, "provider is null");
        this.providerSubject = Objects.requireNonNull(providerSubject, "providerSubject is null");
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        this.authorities = List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
