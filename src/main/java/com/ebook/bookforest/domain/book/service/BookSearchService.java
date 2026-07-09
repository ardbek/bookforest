package com.ebook.bookforest.domain.book.service;

import com.ebook.bookforest.domain.book.dto.BookPlatformSummaryResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchPageResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchResponse;
import com.ebook.bookforest.domain.book.entity.Book;
import com.ebook.bookforest.domain.book.enums.BookSearchType;
import com.ebook.bookforest.domain.book.repository.BookCategorySummary;
import com.ebook.bookforest.domain.book.repository.BookPlatformSummary;
import com.ebook.bookforest.domain.book.repository.BookRepository;
import com.ebook.bookforest.domain.book.repository.BookSearchCondition;
import com.ebook.bookforest.domain.user.repository.FavoriteBookRepository;
import java.util.Comparator;
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
        boolean favoriteYn = findFavoriteBookIds(userId, bookIds).contains(book.getId());

        return BookSearchResponse.from(
            book,
            category,
            libraryPlatforms,
            subscriptionPlatforms,
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
