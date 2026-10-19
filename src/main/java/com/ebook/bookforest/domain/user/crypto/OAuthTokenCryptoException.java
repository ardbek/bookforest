package com.ebook.bookforest.domain.user.crypto;

/**
 * 소셜 플랫폼 토큰 암호화, 복호화 실패
 */
public class OAuthTokenCryptoException extends RuntimeException {

    public OAuthTokenCryptoException(String message) {
        super(message);
    }

    public OAuthTokenCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
