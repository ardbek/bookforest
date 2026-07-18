package com.ebook.bookforest.domain.user.enums;

/**
 * 외부 소셜 계정 연동 해제 작업 상태
 *
 *  PENDING
 *     → PROCESSING
 *         → SUCCEEDED
 *         → RETRY_WAITING
 *             → PROCESSING
 *             → FAILED
 */
public enum SocialUnlinkStatus {
    PENDING, // 처리 대기
    PROCESSING, // 처리중
    RETRY_WAITING, //일시적인 실패로 재시도 대기
    SUCCEEDED, // 연동 해제 성공
    FAILED // 최대 재시도 횟수 초과 또는 복구할 수 없는 실패
}
