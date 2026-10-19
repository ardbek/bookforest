package com.ebook.bookforest.global.security.oauth;

import com.ebook.bookforest.domain.user.enums.SocialProvider;
import java.util.Map;

/**
 * 네이버 사용자 정보 응답
 * 모든 값이 response 객체 아래에 있고, 닉네임이 없으면 이름을 대신 사용
 */
public class NaverResponse implements OAuth2Response {

    private final Map<String, Object> response;

    public NaverResponse(Map<String, Object> attributes) {
        this.response = getMap(attributes, "response");
    }

    @Override
    public SocialProvider getProvider() {
        return SocialProvider.NAVER;
    }

    @Override
    public String getProviderSubject() {
        return getString(response, "id");
    }

    @Override
    public String getEmail() {
        return getString(response, "email");
    }

    @Override
    public String getNickname() {
        String nickname = getString(response, "nickname");
        if (nickname == null || nickname.isBlank()) {
            return getString(response, "name");
        }
        return nickname;
    }

    @Override
    public String getProfileImageUrl() {
        return getString(response, "profile_image");
    }

    private static String getString(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        return Map.of();
    }
}
