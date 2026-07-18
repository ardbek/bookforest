package com.ebook.bookforest.global.security.oauth.unlink;

public class SocialUnlinkClientException extends RuntimeException {

    private final boolean retryable;
    private final String errorCode;

    public SocialUnlinkClientException(
        boolean retryable,
        String errorCode
    ) {
        this(
            retryable,
            errorCode,
            "소셜 계정 연동 해제에 실패했습니다.",
            null
        );
    }

    public SocialUnlinkClientException(
        boolean retryable,
        String errorCode,
        String message
    ) {
        this(retryable, errorCode, message, null);
    }

    public SocialUnlinkClientException(
        boolean retryable,
        String errorCode,
        String message,
        Throwable cause
    ) {
        super(message, cause);

        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("오류 코드는 필수입니다.");
        }

        this.retryable = retryable;
        this.errorCode = errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
