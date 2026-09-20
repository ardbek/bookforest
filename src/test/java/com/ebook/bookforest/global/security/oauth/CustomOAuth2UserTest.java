package com.ebook.bookforest.global.security.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.enums.UserRole;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomOAuth2UserTest {

    private static final Long USER_ID = 1L;
    private static final String PROVIDER_SUBJECT = "kakao-user-id";

    @Test
    void 소셜_플랫폼_사용자_식별값을_보관한다() {
        CustomOAuth2User user = new CustomOAuth2User(
            USER_ID,
            UserRole.ROLE_USER,
            SocialProvider.KAKAO,
            PROVIDER_SUBJECT,
            Map.of("id", PROVIDER_SUBJECT)
        );

        assertEquals(PROVIDER_SUBJECT, user.getProviderSubject());
        assertEquals(String.valueOf(USER_ID), user.getName());
    }

    @Test
    void 소셜_플랫폼_사용자_식별값이_null이면_생성할_수_없다() {
        assertThrows(
            NullPointerException.class,
            () -> new CustomOAuth2User(
                USER_ID,
                UserRole.ROLE_USER,
                SocialProvider.KAKAO,
                null,
                Map.of("id", PROVIDER_SUBJECT)
            )
        );
    }
}
