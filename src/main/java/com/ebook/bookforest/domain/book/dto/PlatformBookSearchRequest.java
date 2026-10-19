package com.ebook.bookforest.domain.book.dto;

import java.util.List;

/**
 * 플랫폼별 검색 요청 조건
 *
 * @param searchType   검색 대상(TITLE, AUTHOR, PUBLISHER), 없으면 전체, 그 외 값은 오류
 * @param platformType 플랫폼 종류(ALL, LIBRARY, SUBSCRIPTION), 대소문자 무시
 * @param platformIds  검색할 플랫폼 ID 목록, 없으면 종류에 맞는 전체 플랫폼
 * @param platformId   한 플랫폼만 페이지 단위로 볼 때의 플랫폼 ID, 없으면 플랫폼마다 미리보기만 조회
 */
public record PlatformBookSearchRequest(
    String keyword,
    String searchType,
    List<Long> categoryIds,
    String platformType,
    List<Long> platformIds,
    Long platformId,
    int page,
    int size
) {
}
