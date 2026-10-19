package com.ebook.bookforest.domain.book.service;

import com.ebook.bookforest.domain.book.dto.BookAvailabilityInfo;
import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.enums.PlatformType;
import com.ebook.bookforest.domain.book.exception.BookErrorCode;
import com.ebook.bookforest.domain.book.exception.BookException;
import com.ebook.bookforest.domain.book.repository.BookPlatformSummary;
import com.ebook.bookforest.domain.book.repository.BookRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인에 제공하는 원본 도서 조회 서비스
 * 검색은 BookSearchService가 담당
 */
@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;

    /**
     * 원본 도서 플랫폼 제공 현황 조회(ISBN 목록, 활성 도서, ID순, 다른 도메인 제공용)
     * 도서관 플랫폼은 이용 가능한 노출 도서관이 있을 때, 구독 플랫폼은 이용 가능할 때만 포함
     */
    @Transactional(readOnly = true)
    public List<BookAvailabilityInfo> findAvailabilitiesByIsbns(List<String> isbns) {
        if (isbns.isEmpty()) {
            return List.of();
        }

        List<BookOrigin> books = bookRepository.findAllByIsbn13RawInAndActiveTrueOrderByIdAsc(isbns);
        List<Long> bookOriginIds = books.stream().map(BookOrigin::getId).toList();
        Map<Long, List<BookAvailabilityInfo.Platform>> platformsByBookId = new LinkedHashMap<>();
        if (!bookOriginIds.isEmpty()) {
            addPlatforms(platformsByBookId, bookRepository.findLibraryPlatformSummaries(bookOriginIds), PlatformType.LIBRARY);
            addPlatforms(
                platformsByBookId,
                bookRepository.findSubscriptionPlatformSummaries(bookOriginIds),
                PlatformType.SUBSCRIPTION
            );
        }

        return books.stream()
            .map(book -> new BookAvailabilityInfo(
                book.getId(),
                book.getIsbn13Raw(),
                platformsByBookId.getOrDefault(book.getId(), List.of())
            ))
            .toList();
    }

    /**
     * 원본 도서 엔티티 참조(연관관계 설정용, 활성 도서)
     * 받는 쪽은 엔티티 상태 변경이나 필드 기반 판단 금지
     *
     * @throws BookException 도서가 없거나 비활성인 경우 BOOK_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public BookOrigin getBookOriginReference(Long bookOriginId) {
        return bookRepository.findById(bookOriginId)
            .filter(BookOrigin::isActive)
            .orElseThrow(() -> new BookException(BookErrorCode.BOOK_NOT_FOUND));
    }

    private void addPlatforms(
        Map<Long, List<BookAvailabilityInfo.Platform>> platformsByBookId,
        List<BookPlatformSummary> summaries,
        PlatformType platformType
    ) {
        for (BookPlatformSummary summary : summaries) {
            platformsByBookId.computeIfAbsent(summary.bookOriginId(), ignored -> new ArrayList<>())
                .add(new BookAvailabilityInfo.Platform(summary.platformId(), summary.platformName(), platformType));
        }
    }
}
