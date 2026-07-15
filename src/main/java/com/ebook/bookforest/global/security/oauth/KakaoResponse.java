package com.ebook.bookforest.global.security.oauth;

import com.ebook.bookforest.domain.user.enums.SocialProvider;
import java.util.Map;

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
        Map<String, Object> kakaoAccount = getMap(attributes, "kakao_account");
        return getString(kakaoAccount, "email");
    }

    @Override
    public String getNickname() {
        Map<String, Object> kakaoAccount = getMap(attributes, "kakao_account");
        Map<String, Object> profile = getMap(kakaoAccount, "profile");

        return getString(profile, "nickname");
    }

    @Override
    public String getProfileImageUrl() {
        Map<String, Object> kakaoAccount =
            getMap(attributes, "kakao_account");

        Map<String, Object> profile =
            getMap(kakaoAccount, "profile");

        return getString(profile, "profile_image_url");
    }

    private static String getString(Map<String, Object> source, String key) {
        Object value = source.get(key);

        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> source, String key) {
        Object value = source.get(key);

        if(value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }

        return Map.of();
    }


}
