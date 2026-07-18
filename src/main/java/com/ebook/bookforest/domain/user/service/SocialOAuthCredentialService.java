package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.user.entity.SocialAccount;
import com.ebook.bookforest.domain.user.entity.SocialOAuthCredential;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.repository.SocialAccountRepository;
import com.ebook.bookforest.domain.user.repository.SocialOAuthCredentialRepository;
import com.ebook.bookforest.global.security.crypto.OAuthTokenCipher;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialOAuthCredentialService {

    private final SocialAccountRepository socialAccountRepository;
    private final SocialOAuthCredentialRepository credentialRepository;
    private final OAuthTokenCipher tokenCipher;

    /**
     * 소셜 플랫폼에서 발급 받은 Access/Refresh Token을 암호화 하여 저장하거나 갱신
     * @param provider
     * @param providerSubject
     * @param accessToken
     * @param refreshToken
     */
    @Transactional
    public void saveOrUpdate(
        SocialProvider provider,
        String providerSubject,
        OAuth2AccessToken accessToken,
        OAuth2RefreshToken refreshToken
    ) {
        validateRequest(provider, providerSubject, accessToken);

        // 카카오는 연동 해제를 Admin Key로 진행하기 때문에 저장 x
        if(provider == SocialProvider.KAKAO) {
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

    /**
     * SocialProvider와 providerSubject에 해당하는 SocialAccount 조회
     */
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

    /**
     * Credential이 없는 계정인 경우 Credential 객체 생성
     */
    private SocialOAuthCredential createCredential(SocialAccount socialAccount) {
        return SocialOAuthCredential.builder()
            .socialAccount(socialAccount)
            .build();
    }

    /**
     * Access Token은 로그인할 때마다 갱신
     */
    private void updateAccessToken(SocialOAuthCredential credential, OAuth2AccessToken accessToken) {
        String encryptedAccessToken = tokenCipher.encrypt(accessToken.getTokenValue());
        credential.updateAccessToken(encryptedAccessToken,accessToken.getExpiresAt());
    }

    /**
     * 새 Refresh Token이 전달된 경우에만 갱신합니다.
     *
     * 소셜 플랫폼은 재로그인할 때 Refresh Token을
     * 다시 내려주지 않을 수 있으므로 null이면 기존 값을 유지합니다.
     */
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
