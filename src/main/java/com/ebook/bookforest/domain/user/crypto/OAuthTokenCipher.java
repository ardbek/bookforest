package com.ebook.bookforest.domain.user.crypto;

/**
 * 소셜 플랫폼 토큰 암호화기, DB에는 암호화한 값만 저장
 */
public interface OAuthTokenCipher {

    String encrypt(String plainToken);

    /**
     * 소셜 플랫폼 토큰 복호화
     *
     * @throws OAuthTokenCryptoException 지원하지 않는 형식이거나 변조, 키 불일치로 복호화하지 못한 경우
     */
    String decrypt(String ciphertext);
}
