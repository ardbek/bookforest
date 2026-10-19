package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.user.entity.User;
import static com.ebook.bookforest.support.EntityIds.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.ebook.bookforest.domain.user.crypto.OAuthTokenCipher;
import com.ebook.bookforest.domain.user.entity.SocialAccount;
import com.ebook.bookforest.domain.user.entity.SocialOAuthCredential;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.repository.SocialAccountRepository;
import com.ebook.bookforest.domain.user.repository.SocialOAuthCredentialRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

@ExtendWith(MockitoExtension.class)
class SocialOAuthCredentialServiceTest {

    private static final Long SOCIAL_ACCOUNT_ID = 10L;
    private static final String PROVIDER_SUBJECT = "naver-user-id";
    private static final String ACCESS_TOKEN_VALUE = "naver-access-token";
    private static final String REFRESH_TOKEN_VALUE = "naver-refresh-token";
    private static final String ENCRYPTED_ACCESS_TOKEN = "v1:encrypted-access";
    private static final String ENCRYPTED_REFRESH_TOKEN = "v1:encrypted-refresh";
    private static final Instant ISSUED_AT =
        Instant.parse("2026-07-16T00:00:00Z");
    private static final Instant EXPIRES_AT =
        Instant.parse("2026-07-16T01:00:00Z");

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private SocialOAuthCredentialRepository credentialRepository;

    @Mock
    private OAuthTokenCipher tokenCipher;

    @InjectMocks
    private SocialOAuthCredentialService credentialService;

    private SocialAccount socialAccount;
    private OAuth2AccessToken accessToken;
    private OAuth2RefreshToken refreshToken;

    @BeforeEach
    void setUp() {
        socialAccount = withId(SocialAccount.builder()
            .user(User.create(null, null, null))
            .socialProvider(SocialProvider.NAVER)
            .providerSubject(PROVIDER_SUBJECT)
            .build(), SOCIAL_ACCOUNT_ID);

        accessToken = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            ACCESS_TOKEN_VALUE,
            ISSUED_AT,
            EXPIRES_AT
        );

        refreshToken = new OAuth2RefreshToken(
            REFRESH_TOKEN_VALUE,
            ISSUED_AT
        );
    }

    @Test
    void 네이버_최초_로그인이면_토큰을_암호화하여_Credential을_저장한다() {
        given(socialAccountRepository.findBySocialProviderAndProviderSubject(
            SocialProvider.NAVER,
            PROVIDER_SUBJECT
        )).willReturn(Optional.of(socialAccount));
        given(credentialRepository.findBySocialAccount_Id(SOCIAL_ACCOUNT_ID))
            .willReturn(Optional.empty());
        given(tokenCipher.encrypt(ACCESS_TOKEN_VALUE))
            .willReturn(ENCRYPTED_ACCESS_TOKEN);
        given(tokenCipher.encrypt(REFRESH_TOKEN_VALUE))
            .willReturn(ENCRYPTED_REFRESH_TOKEN);

        credentialService.saveOrUpdate(
            SocialProvider.NAVER,
            PROVIDER_SUBJECT,
            accessToken,
            refreshToken
        );

        ArgumentCaptor<SocialOAuthCredential> captor =
            ArgumentCaptor.forClass(SocialOAuthCredential.class);
        then(credentialRepository).should().save(captor.capture());

        SocialOAuthCredential savedCredential = captor.getValue();
        assertThat(savedCredential.getSocialAccount()).isSameAs(socialAccount);
        assertThat(savedCredential.getAccessTokenCiphertext()).isEqualTo(ENCRYPTED_ACCESS_TOKEN);
        assertThat(savedCredential.getRefreshTokenCiphertext()).isEqualTo(ENCRYPTED_REFRESH_TOKEN);
        assertThat(savedCredential.getAccessTokenExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void 네이버_재로그인이면_기존_Credential의_토큰을_갱신한다() {
        SocialOAuthCredential existingCredential = withId(SocialOAuthCredential.create(socialAccount), 20L);
        existingCredential.updateAccessToken("v1:old-access", null);
        existingCredential.updateRefreshToken("v1:old-refresh");

        given(socialAccountRepository.findBySocialProviderAndProviderSubject(
            SocialProvider.NAVER,
            PROVIDER_SUBJECT
        )).willReturn(Optional.of(socialAccount));
        given(credentialRepository.findBySocialAccount_Id(SOCIAL_ACCOUNT_ID))
            .willReturn(Optional.of(existingCredential));
        given(tokenCipher.encrypt(ACCESS_TOKEN_VALUE))
            .willReturn(ENCRYPTED_ACCESS_TOKEN);
        given(tokenCipher.encrypt(REFRESH_TOKEN_VALUE))
            .willReturn(ENCRYPTED_REFRESH_TOKEN);

        credentialService.saveOrUpdate(
            SocialProvider.NAVER,
            PROVIDER_SUBJECT,
            accessToken,
            refreshToken
        );

        assertThat(existingCredential.getAccessTokenCiphertext()).isEqualTo(ENCRYPTED_ACCESS_TOKEN);
        assertThat(existingCredential.getRefreshTokenCiphertext()).isEqualTo(ENCRYPTED_REFRESH_TOKEN);
        assertThat(existingCredential.getAccessTokenExpiresAt()).isEqualTo(EXPIRES_AT);
        then(credentialRepository).should().save(existingCredential);
    }

    @Test
    void 새_Refresh_Token이_없으면_기존_Refresh_Token을_유지한다() {
        SocialOAuthCredential existingCredential = withId(SocialOAuthCredential.create(socialAccount), 20L);
        existingCredential.updateAccessToken("v1:old-access", null);
        existingCredential.updateRefreshToken("v1:existing-refresh");

        given(socialAccountRepository.findBySocialProviderAndProviderSubject(
            SocialProvider.NAVER,
            PROVIDER_SUBJECT
        )).willReturn(Optional.of(socialAccount));
        given(credentialRepository.findBySocialAccount_Id(SOCIAL_ACCOUNT_ID))
            .willReturn(Optional.of(existingCredential));
        given(tokenCipher.encrypt(ACCESS_TOKEN_VALUE))
            .willReturn(ENCRYPTED_ACCESS_TOKEN);

        credentialService.saveOrUpdate(
            SocialProvider.NAVER,
            PROVIDER_SUBJECT,
            accessToken,
            null
        );

        assertThat(existingCredential.getAccessTokenCiphertext()).isEqualTo(ENCRYPTED_ACCESS_TOKEN);
        assertThat(existingCredential.getRefreshTokenCiphertext()).isEqualTo("v1:existing-refresh");
        then(tokenCipher).should(never()).encrypt(REFRESH_TOKEN_VALUE);
        then(credentialRepository).should().save(existingCredential);
    }

    @Test
    void 카카오_로그인이면_사용자_토큰을_저장하지_않는다() {
        credentialService.saveOrUpdate(
            SocialProvider.KAKAO,
            "kakao-user-id",
            accessToken,
            refreshToken
        );

        then(socialAccountRepository).shouldHaveNoInteractions();
        then(credentialRepository).shouldHaveNoInteractions();
        then(tokenCipher).shouldHaveNoInteractions();
    }

    @Test
    void 네이버_SocialAccount가_없으면_Credential을_저장할_수_없다() {
        given(socialAccountRepository.findBySocialProviderAndProviderSubject(
            SocialProvider.NAVER,
            PROVIDER_SUBJECT
        )).willReturn(Optional.empty());

        assertThatThrownBy(() -> credentialService.saveOrUpdate(
                SocialProvider.NAVER,
                PROVIDER_SUBJECT,
                accessToken,
                refreshToken
            )).isInstanceOf(IllegalStateException.class);

        then(credentialRepository).shouldHaveNoInteractions();
        then(tokenCipher).shouldHaveNoInteractions();
    }

    @Test
    void 필수_요청값이_없으면_Credential을_저장할_수_없다() {
        assertThatThrownBy(() -> credentialService.saveOrUpdate(
                null,
                PROVIDER_SUBJECT,
                accessToken,
                refreshToken
            )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> credentialService.saveOrUpdate(
                SocialProvider.NAVER,
                " ",
                accessToken,
                refreshToken
            )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> credentialService.saveOrUpdate(
                SocialProvider.NAVER,
                PROVIDER_SUBJECT,
                null,
                refreshToken
            )).isInstanceOf(NullPointerException.class);

        then(socialAccountRepository).shouldHaveNoInteractions();
        then(credentialRepository).shouldHaveNoInteractions();
        then(tokenCipher).shouldHaveNoInteractions();
    }
}
