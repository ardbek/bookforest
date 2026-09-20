package com.ebook.bookforest.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ebook.bookforest.domain.user.entity.SocialAccount;
import com.ebook.bookforest.domain.user.entity.SocialOAuthCredential;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.repository.SocialAccountRepository;
import com.ebook.bookforest.domain.user.repository.SocialOAuthCredentialRepository;
import com.ebook.bookforest.global.security.crypto.OAuthTokenCipher;
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
        socialAccount = SocialAccount.builder()
            .id(SOCIAL_ACCOUNT_ID)
            .socialProvider(SocialProvider.NAVER)
            .providerSubject(PROVIDER_SUBJECT)
            .build();

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
        when(socialAccountRepository.findBySocialProviderAndProviderSubject(
            SocialProvider.NAVER,
            PROVIDER_SUBJECT
        )).thenReturn(Optional.of(socialAccount));
        when(credentialRepository.findBySocialAccount_Id(SOCIAL_ACCOUNT_ID))
            .thenReturn(Optional.empty());
        when(tokenCipher.encrypt(ACCESS_TOKEN_VALUE))
            .thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(tokenCipher.encrypt(REFRESH_TOKEN_VALUE))
            .thenReturn(ENCRYPTED_REFRESH_TOKEN);

        credentialService.saveOrUpdate(
            SocialProvider.NAVER,
            PROVIDER_SUBJECT,
            accessToken,
            refreshToken
        );

        ArgumentCaptor<SocialOAuthCredential> captor =
            ArgumentCaptor.forClass(SocialOAuthCredential.class);
        verify(credentialRepository).save(captor.capture());

        SocialOAuthCredential savedCredential = captor.getValue();
        assertSame(socialAccount, savedCredential.getSocialAccount());
        assertEquals(
            ENCRYPTED_ACCESS_TOKEN,
            savedCredential.getAccessTokenCiphertext()
        );
        assertEquals(
            ENCRYPTED_REFRESH_TOKEN,
            savedCredential.getRefreshTokenCiphertext()
        );
        assertEquals(EXPIRES_AT, savedCredential.getAccessTokenExpiresAt());
    }

    @Test
    void 네이버_재로그인이면_기존_Credential의_토큰을_갱신한다() {
        SocialOAuthCredential existingCredential =
            SocialOAuthCredential.builder()
                .id(20L)
                .socialAccount(socialAccount)
                .accessTokenCiphertext("v1:old-access")
                .refreshTokenCiphertext("v1:old-refresh")
                .build();

        when(socialAccountRepository.findBySocialProviderAndProviderSubject(
            SocialProvider.NAVER,
            PROVIDER_SUBJECT
        )).thenReturn(Optional.of(socialAccount));
        when(credentialRepository.findBySocialAccount_Id(SOCIAL_ACCOUNT_ID))
            .thenReturn(Optional.of(existingCredential));
        when(tokenCipher.encrypt(ACCESS_TOKEN_VALUE))
            .thenReturn(ENCRYPTED_ACCESS_TOKEN);
        when(tokenCipher.encrypt(REFRESH_TOKEN_VALUE))
            .thenReturn(ENCRYPTED_REFRESH_TOKEN);

        credentialService.saveOrUpdate(
            SocialProvider.NAVER,
            PROVIDER_SUBJECT,
            accessToken,
            refreshToken
        );

        assertEquals(
            ENCRYPTED_ACCESS_TOKEN,
            existingCredential.getAccessTokenCiphertext()
        );
        assertEquals(
            ENCRYPTED_REFRESH_TOKEN,
            existingCredential.getRefreshTokenCiphertext()
        );
        assertEquals(EXPIRES_AT, existingCredential.getAccessTokenExpiresAt());
        verify(credentialRepository).save(existingCredential);
    }

    @Test
    void 새_Refresh_Token이_없으면_기존_Refresh_Token을_유지한다() {
        SocialOAuthCredential existingCredential =
            SocialOAuthCredential.builder()
                .id(20L)
                .socialAccount(socialAccount)
                .accessTokenCiphertext("v1:old-access")
                .refreshTokenCiphertext("v1:existing-refresh")
                .build();

        when(socialAccountRepository.findBySocialProviderAndProviderSubject(
            SocialProvider.NAVER,
            PROVIDER_SUBJECT
        )).thenReturn(Optional.of(socialAccount));
        when(credentialRepository.findBySocialAccount_Id(SOCIAL_ACCOUNT_ID))
            .thenReturn(Optional.of(existingCredential));
        when(tokenCipher.encrypt(ACCESS_TOKEN_VALUE))
            .thenReturn(ENCRYPTED_ACCESS_TOKEN);

        credentialService.saveOrUpdate(
            SocialProvider.NAVER,
            PROVIDER_SUBJECT,
            accessToken,
            null
        );

        assertEquals(
            ENCRYPTED_ACCESS_TOKEN,
            existingCredential.getAccessTokenCiphertext()
        );
        assertEquals(
            "v1:existing-refresh",
            existingCredential.getRefreshTokenCiphertext()
        );
        verify(tokenCipher, never()).encrypt(REFRESH_TOKEN_VALUE);
        verify(credentialRepository).save(existingCredential);
    }

    @Test
    void 카카오_로그인이면_사용자_토큰을_저장하지_않는다() {
        credentialService.saveOrUpdate(
            SocialProvider.KAKAO,
            "kakao-user-id",
            accessToken,
            refreshToken
        );

        verifyNoInteractions(
            socialAccountRepository,
            credentialRepository,
            tokenCipher
        );
    }

    @Test
    void 네이버_SocialAccount가_없으면_Credential을_저장할_수_없다() {
        when(socialAccountRepository.findBySocialProviderAndProviderSubject(
            SocialProvider.NAVER,
            PROVIDER_SUBJECT
        )).thenReturn(Optional.empty());

        assertThrows(
            IllegalStateException.class,
            () -> credentialService.saveOrUpdate(
                SocialProvider.NAVER,
                PROVIDER_SUBJECT,
                accessToken,
                refreshToken
            )
        );

        verifyNoInteractions(credentialRepository, tokenCipher);
    }

    @Test
    void 필수_요청값이_없으면_Credential을_저장할_수_없다() {
        assertThrows(
            NullPointerException.class,
            () -> credentialService.saveOrUpdate(
                null,
                PROVIDER_SUBJECT,
                accessToken,
                refreshToken
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> credentialService.saveOrUpdate(
                SocialProvider.NAVER,
                " ",
                accessToken,
                refreshToken
            )
        );
        assertThrows(
            NullPointerException.class,
            () -> credentialService.saveOrUpdate(
                SocialProvider.NAVER,
                PROVIDER_SUBJECT,
                null,
                refreshToken
            )
        );

        verifyNoInteractions(
            socialAccountRepository,
            credentialRepository,
            tokenCipher
        );
    }
}
