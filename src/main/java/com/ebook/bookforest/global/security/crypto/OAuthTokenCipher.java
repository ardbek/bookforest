package com.ebook.bookforest.global.security.crypto;

public interface OAuthTokenCipher {

    /**
     * 소셜 플랫폼 토큰 암호화
     */
    String encrypt(String plainToken);

    /**
     * 소셜 플랫폼 토큰 복호화
     */
    String decrypt(String ciphertext);
}
