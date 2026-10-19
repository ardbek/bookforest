package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.user.crypto.OAuthTokenCipher;
import com.ebook.bookforest.domain.user.entity.SocialAccount;
import com.ebook.bookforest.domain.user.entity.SocialOAuthCredential;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.repository.SocialAccountRepository;
import com.ebook.bookforest.domain.user.repository.SocialOAuthCredentialRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 플랫폼 토큰 저장 서비스
 * 네이버 연동 해제에 필요한 토큰을 로그인할 때마다 암호화해 보관
 */
@Service
@RequiredArgsConstructor
public class SocialOAuthCredentialService {

    private final SocialAccountRepository socialAccountRepository;
    private final SocialOAuthCredentialRepository credentialRepository;
    private final OAuthTokenCipher tokenCipher;

    /**
     * 소셜 플랫폼 토큰 저장 또는 갱신(암호화 후 저장)
     * 카카오는 연동 해제를 Admin Key로 하므로 저장하지 않음
     *
     * @param refreshToken 소셜 플랫폼이 재로그인 때 다시 주지 않을 수 있어 null 허용, null이면 기존 값 유지
     */
    @Transactional
    public void saveOrUpdate(
        SocialProvider provider,
        String providerSubject,
        OAuth2AccessToken accessToken,
        OAuth2RefreshToken refreshToken
    ) {
        validateRequest(provider, providerSubject, accessToken);

        if (provider == SocialProvider.KAKAO) {
            return;
        }

        SocialAccount socialAccount = findSocialAccount(provider, providerSubject);

        SocialOAuthCredential credential =
            credentialRepository
                .findBySocialAccount_Id(socialAccount.getId())
                .orElseGet(() -> createCredential(socialAccount));

        updateAccessToken(credential, accessToken);
        updateRefreshTokenIfPresent(credential, refreshToken);

        credentialRepository.save(credential);

    }

    private SocialAccount findSocialAccount(SocialProvider provider, String providerSubject) {
        return socialAccountRepository
            .findBySocialProviderAndProviderSubject(
                provider,
                providerSubject
            )
            .orElseThrow(() ->
                new IllegalStateException("소셜 계정 정보를 찾을 수 없습니다.")
            );
    }

    private SocialOAuthCredential createCredential(SocialAccount socialAccount) {
        return SocialOAuthCredential.create(socialAccount);
    }

    private void updateAccessToken(SocialOAuthCredential credential, OAuth2AccessToken accessToken) {
        String encryptedAccessToken = tokenCipher.encrypt(accessToken.getTokenValue());
        credential.updateAccessToken(encryptedAccessToken, accessToken.getExpiresAt());
    }

    private void updateRefreshTokenIfPresent(
        SocialOAuthCredential credential,
        OAuth2RefreshToken refreshToken
    ) {
        if (refreshToken == null) {
            return;
        }

        String tokenValue = refreshToken.getTokenValue();
        if (tokenValue == null || tokenValue.isBlank()) {
            return;
        }

        String encryptedRefreshToken = tokenCipher.encrypt(tokenValue);
        credential.updateRefreshToken(encryptedRefreshToken);
    }

    private void validateRequest(SocialProvider provider, String providerSubject, OAuth2AccessToken accessToken) {

        Objects.requireNonNull(provider, "SocialProvider는 필수입니다.");
        Objects.requireNonNull(accessToken, "OAuth2 Access Token은 필수입니다.");

        if (providerSubject == null || providerSubject.isBlank()) {
            throw new IllegalArgumentException("소셜 플랫폼 사용자 식별값은 필수입니다.");
        }
    }

}
