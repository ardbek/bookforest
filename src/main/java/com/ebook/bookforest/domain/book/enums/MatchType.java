package com.ebook.bookforest.domain.book.enums;

public enum MatchType {

    /* ISBN13 일치 */
    ISBN13,

    /* ISBN10 일치 */
    ISBN10,

    /* 메타데이터 기반 */
    METADATA,

    /* 관리자 수동 매칭 */
    MANUAL

}
