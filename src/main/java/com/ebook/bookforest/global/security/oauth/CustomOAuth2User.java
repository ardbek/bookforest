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

public class CustomOAuth2User implements OAuth2User {

    /* userId(sequence) */
    private final Long userId;
    /* 권한 */
    private final UserRole role;
    /* 로그인 플랫폼 */
    private final SocialProvider provider;
    /* 로그인 플랫폼에서 제공한 사용자 정보 */
    private final Map<String, Object> attributes;
    /**/
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomOAuth2User(
        Long userId
        , UserRole role
        , SocialProvider provider
        , Map<String, Object> attributes
    ) {
        this.userId = Objects.requireNonNull(userId, "userId is null");
        this.role = Objects.requireNonNull(role, "role is null");
        this.provider = Objects.requireNonNull(provider, "provider is null");
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes)); // 수정 방지 unmodifiableMap으로 생성
        this.authorities = List.of(new SimpleGrantedAuthority(role.name()));
    }

    public Long getUserId() {
        return userId;
    }

    public UserRole getRole() {
        return role;
    }

    public SocialProvider getProvider() {
        return provider;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }

}
