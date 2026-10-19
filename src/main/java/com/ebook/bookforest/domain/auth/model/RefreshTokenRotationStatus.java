package com.ebook.bookforest.domain.auth.model;

/**
 * Refresh Token 교체 결과
 */
public enum RefreshTokenRotationStatus {

    ROTATED,

    /** 세션이 없거나 만료된 상태 */
    NOT_FOUND,

    /** 이미 교체된 토큰이 유예 기간 이후 다시 제출된 상태, 탈취 의심 */
    REUSE_DETECTED
}
