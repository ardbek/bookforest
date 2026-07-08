package com.ebook.bookforest.domain.category.enums;

public enum MappingStatus {

    /* 수집 직후, 표준 카테고리 미매핑 */
    UNMAPPED,

    /* 자동 매핑 배치가 후보를 지정한 상태 (관리자 검토 대기) */
    AUTO_MAPPED,

    /* 관리자가 매핑을 확정한 상태 */
    CONFIRMED,

    /* 매핑 제외 대상 (테스트 카테고리, 잡지 등) */
    IGNORED
}
