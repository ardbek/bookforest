package com.ebook.bookforest.global.security.crypto;

public class OAuthTokenCryptoException extends RuntimeException {

    public OAuthTokenCryptoException(String message) {
        super(message);
    }

    public OAuthTokenCryptoException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}
