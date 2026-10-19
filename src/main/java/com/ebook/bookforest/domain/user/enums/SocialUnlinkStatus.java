package com.ebook.bookforest.domain.user.enums;

/**
 * 소셜 연동 해제 작업 상태
 */
public enum SocialUnlinkStatus {

    /** 처리 대기 */
    PENDING,

    /** 처리 중, 외부 API 호출 진행 */
    PROCESSING,

    /** 일시적인 실패로 재시도 대기 */
    RETRY_WAITING,

    /** 연동 해제 성공 */
    SUCCEEDED,

    /** 재시도 소진 또는 복구할 수 없는 실패 */
    FAILED
}
