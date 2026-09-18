package com.ebook.bookforest.domain.book.service;

import static com.ebook.bookforest.domain.book.service.BookSearchFilters.sanitizeIds;

import com.ebook.bookforest.domain.book.dto.BookPlatformSummaryResponse;
import com.ebook.bookforest.domain.book.dto.BookLibraryHoldingResponse;
import com.ebook.bookforest.domain.book.dto.BookLibraryPlatformHoldingResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchPageResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchResponse;
import com.ebook.bookforest.domain.book.dto.BookSubscriptionPlatformResponse;
import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.enums.BookSearchType;
import com.ebook.bookforest.domain.book.exception.BookErrorCode;
import com.ebook.bookforest.domain.book.exception.BookException;
import com.ebook.bookforest.domain.book.repository.BookCategorySummary;
import com.ebook.bookforest.domain.book.repository.BookLibraryHolding;
import com.ebook.bookforest.domain.book.repository.BookPlatformSummary;
import com.ebook.bookforest.domain.book.repository.BookRepository;
import com.ebook.bookforest.domain.book.repository.BookSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookSearchService {

    private final BookRepository bookRepository;

    // 도서 ID로 분류와 이용 가능한 플랫폼 상세 정보를 조회한다.
    @Transactional(readOnly = true)
    public BookSearchResponse getBook(Long bookOriginId) {
        BookOrigin bookOrigin = bookRepository.findById(bookOriginId)
            .filter(BookOrigin::isActive)
            .orElseThrow(() -> new BookException(BookErrorCode.BOOK_NOT_FOUND));

        List<Long> bookOriginIds = List.of(bookOrigin.getId());
        BookCategorySummary category = bookRepository.findCategorySummaries(bookOriginIds)
            .stream()
            .findFirst()
            .orElse(null);
        List<BookPlatformSummaryResponse> libraryPlatforms =
            groupPlatformSummaries(bookRepository.findLibraryPlatformSummaries(bookOriginIds))
                .getOrDefault(bookOrigin.getId(), List.of());
        List<BookPlatformSummaryResponse> subscriptionPlatforms =
            groupPlatformSummaries(bookRepository.findSubscriptionPlatformSummaries(bookOriginIds))
                .getOrDefault(bookOrigin.getId(), List.of());
        List<BookSubscriptionPlatformResponse> subscriptionPlatformLinks = bookRepository
            .findSubscriptionPlatformLinks(bookOriginIds)
            .stream()
            .map(BookSubscriptionPlatformResponse::from)
            .toList();
        List<BookLibraryHolding> holdings = bookRepository.findLibraryHoldings(bookOriginIds);
        List<BookLibraryPlatformHoldingResponse> libraryPlatformHoldings = holdings.isEmpty()
            ? List.of()
            : List.of(BookLibraryPlatformHoldingResponse.of(
                holdings.get(0).platformId(),
                holdings.get(0).platformCode(),
                holdings.get(0).platformName(),
                holdings.stream().map(BookLibraryHoldingResponse::from).toList()
            ));

        return BookSearchResponse.from(
            bookOrigin,
            category,
            libraryPlatforms,
            subscriptionPlatforms,
            subscriptionPlatformLinks,
            libraryPlatformHoldings,
            false
        );
    }

    // 일반 도서 검색 결과에 분류와 플랫폼 요약을 함께 반환한다.
    @Transactional(readOnly = true)
    public BookSearchPageResponse searchBooks(
        String keyword,
        String searchType,
        List<Long> categoryIds,
        List<Long> libraryIds,
        List<Long> libraryPlatformIds,
        List<Long> subscriptionPlatformIds,
        Pageable pageable
    ) {
        BookSearchCondition condition = new BookSearchCondition(
            keyword,
            BookSearchType.from(searchType),
            sanitizeIds(categoryIds),
            sanitizeIds(libraryIds),
            sanitizeIds(libraryPlatformIds),
            sanitizeIds(subscriptionPlatformIds),
            null
        );

        Page<BookOrigin> bookOrigins = bookRepository.searchBooks(condition, pageable);
        List<Long> bookOriginIds = bookOrigins.getContent().stream()
            .map(BookOrigin::getId)
            .toList();

        Map<Long, BookCategorySummary> categoriesByBookOriginId = bookRepository
            .findCategorySummaries(bookOriginIds)
            .stream()
            .collect(Collectors.toMap(
                BookCategorySummary::bookOriginId,
                Function.identity(),
                (first, ignored) -> first
            ));

        Map<Long, List<BookPlatformSummaryResponse>> libraryPlatformsByBookOriginId =
            groupPlatformSummaries(bookRepository.findLibraryPlatformSummaries(bookOriginIds));
        Map<Long, List<BookPlatformSummaryResponse>> subscriptionPlatformsByBookOriginId =
            groupPlatformSummaries(bookRepository.findSubscriptionPlatformSummaries(bookOriginIds));

        return BookSearchPageResponse.from(bookOrigins.map(bookOrigin ->
            BookSearchResponse.from(
                bookOrigin,
                categoriesByBookOriginId.get(bookOrigin.getId()),
                libraryPlatformsByBookOriginId.getOrDefault(bookOrigin.getId(), List.of()),
                subscriptionPlatformsByBookOriginId.getOrDefault(bookOrigin.getId(), List.of()),
                false
            )
        ));
    }

    // 도서별 플랫폼 요약을 응답 목록으로 변환한다.
    private Map<Long, List<BookPlatformSummaryResponse>> groupPlatformSummaries(
        List<BookPlatformSummary> summaries
    ) {
        return summaries.stream()
            .collect(Collectors.toMap(
                BookPlatformSummary::bookOriginId,
                summary -> List.of(new BookPlatformSummaryResponse(
                    summary.platformId(),
                    summary.platformCode(),
                    summary.platformName(),
                    summary.itemCount()
                ))
            ));
    }

}
