package com.ebook.bookforest.domain.book.enums;

/**
 * 도서관 보유, 구독 제공 정보의 이용 상태
 */
public enum AvailabilityStatus {

    /** 현재 이용 가능 */
    AVAILABLE,

    /** 더 이상 제공하지 않음 */
    REMOVED,

    /** 상태 확인 불가 */
    UNKNOWN
}
