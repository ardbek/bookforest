package com.ebook.bookforest.domain.user.client;

import lombok.Getter;

/**
 * 소셜 연동 해제 API 호출 실패
 * 연동 해제 작업 처리기가 재시도 가능 여부와 오류 코드를 작업 상태에 기록
 */
@Getter
public class SocialUnlinkClientException extends RuntimeException {

    private final boolean retryable;

    /** 작업 기록용 오류 코드(KAKAO_HTTP_500, NAVER_NETWORK_ERROR 등) */
    private final String errorCode;

    public SocialUnlinkClientException(boolean retryable, String errorCode) {
        this(retryable, errorCode, "소셜 계정 연동 해제에 실패했습니다.", null);
    }

    public SocialUnlinkClientException(boolean retryable, String errorCode, String message) {
        this(retryable, errorCode, message, null);
    }

    public SocialUnlinkClientException(boolean retryable, String errorCode, String message, Throwable cause) {
        super(message, cause);
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("오류 코드는 필수입니다.");
        }
        this.retryable = retryable;
        this.errorCode = errorCode;
    }
}
