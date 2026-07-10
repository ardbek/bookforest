package com.ebook.bookforest.domain.book.service;

import com.ebook.bookforest.domain.book.dto.BookPlatformSummaryResponse;
import com.ebook.bookforest.domain.book.dto.BookLibraryHoldingResponse;
import com.ebook.bookforest.domain.book.dto.BookLibraryPlatformHoldingResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchPageResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchResponse;
import com.ebook.bookforest.domain.book.dto.BookSubscriptionPlatformResponse;
import com.ebook.bookforest.domain.book.entity.Book;
import com.ebook.bookforest.domain.book.enums.BookSearchType;
import com.ebook.bookforest.domain.book.repository.BookCategorySummary;
import com.ebook.bookforest.domain.book.repository.BookLibraryHolding;
import com.ebook.bookforest.domain.book.repository.BookPlatformSummary;
import com.ebook.bookforest.domain.book.repository.BookRepository;
import com.ebook.bookforest.domain.book.repository.BookSearchCondition;
import com.ebook.bookforest.domain.book.repository.BookSubscriptionPlatformLink;
import com.ebook.bookforest.domain.user.repository.FavoriteBookRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class BookSearchService {

    private final BookRepository bookRepository;
    private final FavoriteBookRepository favoriteBookRepository;

    @Transactional(readOnly = true)
    public BookSearchResponse getBook(Long bookId, Long userId) {
        Book book = bookRepository.findById(bookId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "도서를 찾을 수 없습니다: " + bookId
            ));

        List<Long> bookIds = List.of(book.getId());
        BookCategorySummary category = bookRepository.findPrimaryCategorySummaries(bookIds)
            .stream()
            .findFirst()
            .orElse(null);
        List<BookPlatformSummaryResponse> libraryPlatforms =
            groupPlatformSummaries(bookRepository.findLibraryPlatformSummaries(bookIds))
                .getOrDefault(book.getId(), List.of());
        List<BookPlatformSummaryResponse> subscriptionPlatforms =
            groupPlatformSummaries(bookRepository.findSubscriptionPlatformSummaries(bookIds))
                .getOrDefault(book.getId(), List.of());
        List<BookSubscriptionPlatformResponse> subscriptionPlatformLinks =
            resolveSubscriptionPlatformLinks(bookRepository.findSubscriptionPlatformLinks(bookIds));
        List<BookLibraryPlatformHoldingResponse> libraryPlatformHoldings =
            resolveLibraryPlatformHoldings(bookRepository.findLibraryHoldings(bookIds));
        boolean favoriteYn = findFavoriteBookIds(userId, bookIds).contains(book.getId());

        return BookSearchResponse.from(
            book,
            category,
            libraryPlatforms,
            subscriptionPlatforms,
            subscriptionPlatformLinks,
            libraryPlatformHoldings,
            favoriteYn
        );
    }

    @Transactional(readOnly = true)
    public BookSearchPageResponse searchBooks(
        String keyword,
        String searchType,
        List<Long> categoryIds,
        List<Long> libraryIds,
        List<Long> libraryPlatformIds,
        List<Long> subscriptionPlatformIds,
        Long userId,
        Pageable pageable
    ) {
        BookSearchCondition condition = new BookSearchCondition(
            keyword,
            BookSearchType.from(searchType),
            sanitizeIds(categoryIds),
            sanitizeIds(libraryIds),
            sanitizeIds(libraryPlatformIds),
            sanitizeIds(subscriptionPlatformIds)
        );

        Page<Book> books = bookRepository.searchBooks(condition, pageable);
        List<Long> bookIds = books.getContent().stream()
            .map(Book::getId)
            .toList();

        Map<Long, BookCategorySummary> categoriesByBookId = bookRepository
            .findPrimaryCategorySummaries(bookIds)
            .stream()
            .collect(Collectors.toMap(
                BookCategorySummary::bookId,
                Function.identity(),
                (first, ignored) -> first
            ));

        Map<Long, List<BookPlatformSummaryResponse>> libraryPlatformsByBookId = groupPlatformSummaries(bookRepository.findLibraryPlatformSummaries(bookIds));
        Map<Long, List<BookPlatformSummaryResponse>> subscriptionPlatformsByBookId = groupPlatformSummaries(bookRepository.findSubscriptionPlatformSummaries(bookIds));
        Set<Long> favoriteBookIds = findFavoriteBookIds(userId, bookIds);

        List<BookSearchResponse> content = books.getContent().stream()
            .map(book -> BookSearchResponse.from(
                book,
                categoriesByBookId.get(book.getId()),
                libraryPlatformsByBookId.getOrDefault(book.getId(), List.of()),
                subscriptionPlatformsByBookId.getOrDefault(book.getId(), List.of()),
                favoriteBookIds.contains(book.getId())
            ))
            .toList();

        return BookSearchPageResponse.from(new PageImpl<>(
            content,
            pageable,
            books.getTotalElements()
        ));
    }

    private Map<Long, List<BookPlatformSummaryResponse>> groupPlatformSummaries(
        List<BookPlatformSummary> summaries
    ) {
        return summaries.stream()
            .collect(Collectors.groupingBy(
                BookPlatformSummary::bookId,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    values -> values.stream()
                        .sorted(Comparator.comparing(BookPlatformSummary::platformName)
                            .thenComparing(BookPlatformSummary::platformId))
                        .map(summary -> new BookPlatformSummaryResponse(
                            summary.platformId(),
                            summary.platformCode(),
                            summary.platformName(),
                            summary.itemCount()
                        ))
                        .toList()
                )
            ));
    }

    private List<BookSubscriptionPlatformResponse> resolveSubscriptionPlatformLinks(
        List<BookSubscriptionPlatformLink> links
    ) {
        Map<Long, BookSubscriptionPlatformResponse> platformsById = new LinkedHashMap<>();

        for (BookSubscriptionPlatformLink link : links) {
            BookSubscriptionPlatformResponse next = BookSubscriptionPlatformResponse.from(link);
            BookSubscriptionPlatformResponse current = platformsById.get(link.platformId());

            if (current == null || (isBlank(current.bookUrl()) && !isBlank(next.bookUrl()))) {
                platformsById.put(link.platformId(), next);
            }
        }

        return List.copyOf(platformsById.values());
    }

    private List<BookLibraryPlatformHoldingResponse> resolveLibraryPlatformHoldings(
        List<BookLibraryHolding> holdings
    ) {
        Map<Long, LibraryPlatformHoldingAccumulator> platformsById = new LinkedHashMap<>();

        for (BookLibraryHolding holding : holdings) {
            LibraryPlatformHoldingAccumulator platform = platformsById.computeIfAbsent(
                holding.platformId(),
                ignored -> new LibraryPlatformHoldingAccumulator(
                    holding.platformId(),
                    holding.platformCode(),
                    holding.platformName()
                )
            );

            BookLibraryHoldingResponse next = BookLibraryHoldingResponse.from(holding);
            BookLibraryHoldingResponse current = platform.librariesById.get(holding.libraryId());

            if (current == null || shouldReplaceLibraryHolding(current, next)) {
                platform.librariesById.put(holding.libraryId(), next);
            }
        }

        return platformsById.values()
            .stream()
            .map(platform -> BookLibraryPlatformHoldingResponse.of(
                platform.platformId,
                platform.platformCode,
                platform.platformName,
                List.copyOf(platform.librariesById.values())
            ))
            .toList();
    }

    private boolean shouldReplaceLibraryHolding(
        BookLibraryHoldingResponse current,
        BookLibraryHoldingResponse next
    ) {
        return (isBlank(current.bookUrl()) && !isBlank(next.bookUrl()))
            || (isBlank(current.coverImgUrl()) && !isBlank(next.coverImgUrl()))
            || (isBlank(current.siteUrl()) && !isBlank(next.siteUrl()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static class LibraryPlatformHoldingAccumulator {

        private final Long platformId;
        private final String platformCode;
        private final String platformName;
        private final Map<Long, BookLibraryHoldingResponse> librariesById = new LinkedHashMap<>();

        private LibraryPlatformHoldingAccumulator(
            Long platformId,
            String platformCode,
            String platformName
        ) {
            this.platformId = platformId;
            this.platformCode = platformCode;
            this.platformName = platformName;
        }
    }

    /**
     * 즐겨찾기 도서 조회
     * @param userId
     * @param bookIds
     * @return
     */
    private Set<Long> findFavoriteBookIds(Long userId, List<Long> bookIds) {
        if (userId == null || bookIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(favoriteBookRepository.findBookIdsByUserIdAndBookIdIn(userId, bookIds));
    }

    /**
     * 유효한 ID 필터링
     * @param ids
     * @return
     */
    private List<Long> sanitizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .toList();
    }
}
