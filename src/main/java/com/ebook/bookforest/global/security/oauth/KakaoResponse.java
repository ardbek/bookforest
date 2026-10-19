package com.ebook.bookforest.global.security.oauth;

import com.ebook.bookforest.domain.user.enums.SocialProvider;
import java.util.Map;

/**
 * 카카오 사용자 정보 응답
 * 식별값은 최상위 id, 이메일은 kakao_account, 닉네임과 프로필 이미지는 kakao_account.profile 아래에 있음
 */
public class KakaoResponse implements OAuth2Response {

    private final Map<String, Object> attributes;

    public KakaoResponse(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public SocialProvider getProvider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public String getProviderSubject() {
        return getString(attributes, "id");
    }

    @Override
    public String getEmail() {
        return getString(getKakaoAccount(), "email");
    }

    @Override
    public String getNickname() {
        return getString(getProfile(), "nickname");
    }

    @Override
    public String getProfileImageUrl() {
        return getString(getProfile(), "profile_image_url");
    }

    private Map<String, Object> getKakaoAccount() {
        return getMap(attributes, "kakao_account");
    }

    private Map<String, Object> getProfile() {
        return getMap(getKakaoAccount(), "profile");
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
