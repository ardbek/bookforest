package com.ebook.bookforest.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebook.bookforest.domain.user.entity.User;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.exception.UserErrorCode;
import com.ebook.bookforest.domain.user.exception.UserException;
import com.ebook.bookforest.global.security.oauth.OAuth2Response;
import com.ebook.bookforest.support.MySqlDataJpaTest;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 소셜 로그인 시 가입과 로그인 처리 동작 검증
 * 엔티티 생성 방식이 바뀌어도 동작을 고정하도록 실제 DB를 쓰고 준비 데이터는 SQL로 입력
 */
@MySqlDataJpaTest
@Import(OAuthService.class)
class OAuthServiceIntegrationTest {

    private static final String SUBJECT = "naver-user-id";

    @Autowired
    private OAuthService oAuthService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("처음 로그인한 소셜 사용자는 사용자와 소셜 계정을 새로 만들고 소셜 프로필로 채움")
    void loginOrSignUpCreatesUserAndSocialAccount() {
        // when
        User user = oAuthService.loginOrSignUp(response(SUBJECT, "책벌레", "reader@example.com"));
        entityManager.flush();

        // then
        assertThat(user.getId()).isNotNull();
        assertThat(user.getDisplayName()).isEqualTo("책벌레");
        assertThat(userRow(user.getId())).containsEntry("display_name", "책벌레")
            .containsEntry("email", "reader@example.com")
            .containsEntry("profile_image_url", "https://profile.example/책벌레")
            .containsEntry("role", "ROLE_USER");
        assertThat(socialAccountRow(SUBJECT)).containsEntry("user_id", user.getId())
            .containsEntry("social_provider", "NAVER")
            .containsEntry("nickname", "책벌레")
            .containsEntry("email", "reader@example.com")
            .containsEntry("profile_image_url", "https://profile.example/책벌레");
    }

    @Test
    @DisplayName("기존 소셜 계정으로 로그인하면 새로 만들지 않고 사용자와 소셜 계정 프로필을 최신 값으로 갱신")
    void loginOrSignUpUpdatesExistingProfiles() {
        // given
        insertUser(1001L, true);
        insertSocialAccount(2001L, 1001L, SUBJECT);

        // when
        User user = oAuthService.loginOrSignUp(response(SUBJECT, "새 닉네임", "new@example.com"));
        entityManager.flush();

        // then
        assertThat(user.getId()).isEqualTo(1001L);
        assertThat(count("users")).isEqualTo(1);
        assertThat(userRow(1001L)).containsEntry("display_name", "새 닉네임")
            .containsEntry("email", "new@example.com");
        assertThat(socialAccountRow(SUBJECT)).containsEntry("nickname", "새 닉네임")
            .containsEntry("email", "new@example.com")
            .containsEntry("profile_image_url", "https://profile.example/새 닉네임");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "PROCESSING", "RETRY_WAITING"})
    @DisplayName("연동 해제가 끝나지 않은 소셜 계정은 로그인과 가입 모두 차단, 사용자 미생성")
    void loginOrSignUpRejectsUnlinkingAccount(String status) {
        // given
        insertUnlinkTask(SUBJECT, status);

        // when, then
        assertThatThrownBy(() -> oAuthService.loginOrSignUp(response(SUBJECT, "책벌레", "reader@example.com")))
            .isInstanceOfSatisfying(UserException.class, exception -> assertThat(exception.getErrorCode())
                .isEqualTo(UserErrorCode.SOCIAL_UNLINK_IN_PROGRESS));
        assertThat(count("users")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCEEDED", "FAILED"})
    @DisplayName("연동 해제 작업이 끝난 소셜 계정은 다시 가입 가능")
    void loginOrSignUpAllowsSignUpAfterUnlinkFinished(String status) {
        // given
        insertUnlinkTask(SUBJECT, status);

        // when
        oAuthService.loginOrSignUp(response(SUBJECT, "책벌레", "reader@example.com"));
        entityManager.flush();

        // then
        assertThat(count("users")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    @DisplayName("소셜 사용자 식별값이 비어 있으면 IllegalArgumentException")
    void loginOrSignUpRejectsBlankSubject(String subject) {
        // when, then
        assertThatThrownBy(() -> oAuthService.loginOrSignUp(response(subject, "책벌레", null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(count("users")).isZero();
    }

    @Test
    @DisplayName("소셜 계정에 연결된 사용자가 비활성이면 IllegalStateException")
    void loginOrSignUpRejectsInactiveUser() {
        // given
        insertUser(1001L, false);
        insertSocialAccount(2001L, 1001L, SUBJECT);

        // when, then
        assertThatThrownBy(() -> oAuthService.loginOrSignUp(response(SUBJECT, "책벌레", null)))
            .isInstanceOf(IllegalStateException.class);
    }

    private OAuth2Response response(String subject, String nickname, String email) {
        return new TestOAuth2Response(subject, nickname, email, "https://profile.example/" + nickname);
    }

    private void insertUser(long userId, boolean active) {
        jdbcTemplate.update(
            "insert into users (user_id, is_active, role, display_name) values (?, ?, 'ROLE_USER', '이전 닉네임')",
            userId, active
        );
    }

    private void insertSocialAccount(long socialAccountId, long userId, String subject) {
        jdbcTemplate.update("""
            insert into social_account (social_account_id, user_id, social_provider, provider_subject, nickname)
            values (?, ?, 'NAVER', ?, '이전 닉네임')
            """, socialAccountId, userId, subject);
    }

    private void insertUnlinkTask(String subject, String status) {
        jdbcTemplate.update("""
            insert into social_unlink_task (social_provider, provider_subject, status, retry_count)
            values ('NAVER', ?, ?, 0)
            """, subject, status);
    }

    private Map<String, Object> userRow(long userId) {
        return jdbcTemplate.queryForMap(
            "select display_name, email, profile_image_url, role from users where user_id = ?", userId
        );
    }

    private Map<String, Object> socialAccountRow(String subject) {
        return jdbcTemplate.queryForMap("""
            select user_id, social_provider, nickname, email, profile_image_url
            from social_account where provider_subject = ?
            """, subject);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private record TestOAuth2Response(
        String providerSubject,
        String nickname,
        String email,
        String profileImageUrl
    ) implements OAuth2Response {

        @Override
        public SocialProvider getProvider() {
            return SocialProvider.NAVER;
        }

        @Override
        public String getProviderSubject() {
            return providerSubject;
        }

        @Override
        public String getEmail() {
            return email;
        }

        @Override
        public String getNickname() {
            return nickname;
        }

        @Override
        public String getProfileImageUrl() {
            return profileImageUrl;
        }
    }
}
