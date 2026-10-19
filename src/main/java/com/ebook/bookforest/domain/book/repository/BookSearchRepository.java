package com.ebook.bookforest.domain.book.repository;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 도서 검색 화면용 조회 Repository
 * 도서관, 구독, 카테고리, 즐겨찾기 테이블을 조인하는 읽기 전용 조회 모음(컨벤션 2.5)
 */
public interface BookSearchRepository {

    /** 원본 도서 검색(활성 도서, 제목순) */
    Page<BookOrigin> searchBooks(BookSearchCondition condition, Pageable pageable);

    /** 도서관 플랫폼별 보유 도서관 수 조회(이용 가능, 노출 도서관) */
    List<BookPlatformSummary> findLibraryPlatformSummaries(List<Long> bookOriginIds);

    /** 구독 플랫폼별 제공 건수 조회(이용 가능) */
    List<BookPlatformSummary> findSubscriptionPlatformSummaries(List<Long> bookOriginIds);

    /** 구독 플랫폼 이동 링크 조회(이용 가능) */
    List<BookSubscriptionPlatformLink> findSubscriptionPlatformLinks(List<Long> bookOriginIds);

    /** 도서관 보유 현황 조회(이용 가능, 노출 도서관, 모든 도서관 플랫폼), 도서 상세용 */
    List<BookLibraryHolding> findLibraryHoldings(List<Long> bookOriginIds);

    /** 도서관 보유 현황 조회(모든 이용 상태, 노출 도서관, 원본 도서의 플랫폼만), 플랫폼별 검색과 상세용 */
    List<BookLibraryHolding> findAllLibraryHoldings(List<Long> bookOriginIds);

    /** 구독 도서의 이동 링크와 표지 조회 */
    List<BookSubscriptionAsset> findSubscriptionAssets(List<Long> bookOriginIds);

    /** 표준 카테고리 조회(활성, 정렬 순서순) */
    List<BookCategorySummary> findCategorySummaries(List<Long> bookOriginIds);

    /** 즐겨찾기 원본 도서 ID 조회(사용자별), 사용자 도메인을 호출하지 않기 위한 읽기 전용 조회 */
    Set<Long> findFavoriteBookOriginIds(Long userId);

    /** 즐겨찾기 도서관 ID 조회(사용자별), 사용자 도메인을 호출하지 않기 위한 읽기 전용 조회 */
    Set<Long> findFavoriteLibraryIds(Long userId);
}
