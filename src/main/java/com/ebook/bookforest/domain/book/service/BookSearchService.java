package com.ebook.bookforest.domain.book.service;

import com.ebook.bookforest.domain.book.dto.BookPlatformSummaryResponse;
import com.ebook.bookforest.domain.book.dto.BookLibraryHoldingResponse;
import com.ebook.bookforest.domain.book.dto.BookLibraryPlatformHoldingResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchPageResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchResponse;
import com.ebook.bookforest.domain.book.dto.BookSubscriptionPlatformResponse;
import com.ebook.bookforest.domain.book.dto.PlatformBookDetailResponse;
import com.ebook.bookforest.domain.book.dto.PlatformBookSearchResponse;
import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;
import com.ebook.bookforest.domain.book.enums.BookSearchType;
import com.ebook.bookforest.domain.book.exception.BookErrorCode;
import com.ebook.bookforest.domain.book.exception.BookException;
import com.ebook.bookforest.domain.book.repository.BookCategorySummary;
import com.ebook.bookforest.domain.book.repository.BookLibraryHolding;
import com.ebook.bookforest.domain.book.repository.BookPlatformSummary;
import com.ebook.bookforest.domain.book.repository.BookRepository;
import com.ebook.bookforest.domain.book.repository.BookSearchCondition;
import com.ebook.bookforest.domain.book.repository.BookSubscriptionAsset;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.domain.platform.repository.PlatformRepository;
import com.ebook.bookforest.domain.user.repository.FavoriteBookRepository;
import com.ebook.bookforest.domain.user.repository.FavoriteLibraryRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookSearchService {

    private final BookRepository bookRepository;
    private final PlatformRepository platformRepository;
    private final FavoriteBookRepository favoriteBookRepository;
    private final FavoriteLibraryRepository favoriteLibraryRepository;

    @Transactional(readOnly = true)
    public PlatformBookSearchResponse searchPlatformBooks(
        String keyword,
        String searchType,
        List<Long> categoryIds,
        String platformType,
        List<Long> platformIds,
        Long platformId,
        int page,
        int size,
        Long userId
    ) {
        if (keyword == null || keyword.isBlank() || page < 0 || size < 1 || size > 100) {
            throw new BookException(BookErrorCode.INVALID_PLATFORM_BOOK_REQUEST);
        }

        PlatformType requestedType = PlatformType.from(platformType);
        BookSearchType requestedSearchType = strictSearchType(searchType);
        List<Platform> allPlatforms = platformRepository.findAllByOrderByPlatformNameAsc();
        Set<Long> selectedIds = new LinkedHashSet<>(sanitizeIds(platformIds));
        Set<Long> knownPlatformIds = allPlatforms.stream()
            .map(Platform::getId)
            .collect(Collectors.toSet());
        if (!knownPlatformIds.containsAll(selectedIds)) {
            throw new BookException(BookErrorCode.PLATFORM_NOT_FOUND);
        }
        Set<Long> effectiveSelectedIds = allPlatforms.stream()
            .filter(platform -> requestedType.matches(platformTypeOf(platform)))
            .map(Platform::getId)
            .filter(selectedIds::contains)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Platform> platforms;
        if (platformId != null) {
            Platform platform = platformRepository.findById(platformId)
                .orElseThrow(() -> new BookException(BookErrorCode.PLATFORM_NOT_FOUND));
            PlatformType actualType = platformTypeOf(platform);
            if (!requestedType.matches(actualType)
                || (!effectiveSelectedIds.isEmpty() && !effectiveSelectedIds.contains(platformId))) {
                throw new BookException(BookErrorCode.INVALID_PLATFORM_BOOK_REQUEST);
            }
            platforms = List.of(platform);
        } else {
            platforms = allPlatforms.stream()
                .filter(platform -> requestedType.matches(platformTypeOf(platform)))
                .filter(platform -> effectiveSelectedIds.isEmpty()
                    || effectiveSelectedIds.contains(platform.getId()))
                .toList();
        }

        List<PlatformBookSearchResponse.Group> groups = new ArrayList<>();
        for (Platform platform : platforms) {
            PlatformType actualType = platformTypeOf(platform);
            Page<BookOrigin> books = searchPlatform(
                keyword.trim(),
                requestedSearchType,
                sanitizeIds(categoryIds),
                platform,
                actualType,
                platformId == null ? PageRequest.of(0, 3) : PageRequest.of(page, size)
            );
            if (platformId != null || !books.isEmpty()) {
                groups.add(new PlatformBookSearchResponse.Group(
                    platform.getId(),
                    platform.getPlatformCode(),
                    platform.getPlatformName(),
                    actualType.name(),
                    books.getTotalElements(),
                    books.isLast(),
                    toSearchItems(books.getContent(), actualType, userId)
                ));
            }
        }

        return new PlatformBookSearchResponse(
            groups.stream().mapToLong(PlatformBookSearchResponse.Group::totalElements).sum(),
            groups
        );
    }

    @Transactional(readOnly = true)
    public PlatformBookDetailResponse getPlatformBook(
        Long platformId,
        String sourceBookId,
        Long userId
    ) {
        platformRepository.findById(platformId)
            .orElseThrow(() -> new BookException(BookErrorCode.PLATFORM_NOT_FOUND));
        BookOrigin book = bookRepository
            .findByPlatform_IdAndPlatformBookIdAndActiveTrue(platformId, sourceBookId)
            .orElseThrow(() -> new BookException(BookErrorCode.PLATFORM_BOOK_NOT_FOUND));
        PlatformType type = platformTypeOf(book.getPlatform());
        if (type == null) {
            throw new BookException(BookErrorCode.PLATFORM_BOOK_NOT_FOUND);
        }

        List<BookLibraryHolding> holdings = type == PlatformType.LIBRARY
            ? bookRepository.findAllLibraryHoldings(List.of(book.getId()))
            : List.of();
        BookSubscriptionAsset asset = type == PlatformType.SUBSCRIPTION
            ? bookRepository.findSubscriptionAssets(List.of(book.getId())).stream().findFirst().orElse(null)
            : null;
        Set<Long> favoriteBookIds = favoriteBookIds(userId);
        Set<Long> favoriteLibraryIds = favoriteLibraryIds(userId);
        long availableLibraryCount = holdings.stream()
            .filter(holding -> holding.availabilityStatus() == AvailabilityStatus.AVAILABLE)
            .count();

        return new PlatformBookDetailResponse(
            book.getPlatform().getId(),
            book.getPlatform().getPlatformCode(),
            book.getPlatform().getPlatformName(),
            type.name(),
            book.getPlatformBookId(),
            book.getId(),
            book.getContentType(),
            book.getTitleRaw(),
            book.getAuthorRaw(),
            book.getPublisherRaw(),
            book.getPublishedDate(),
            book.getIsbn13Raw(),
            asset != null ? asset.coverImgUrl() : book.getCoverImgUrl(),
            asset != null ? asset.bookUrl() : book.getBookUrl(),
            holdings.size(),
            availableLibraryCount,
            favoriteBookIds.contains(book.getId()),
            book.getDescriptionRaw(),
            holdings.stream().map(holding -> new PlatformBookDetailResponse.LibraryItem(
                holding.libraryId(),
                holding.libraryCode(),
                holding.libraryName(),
                holding.siteUrl(),
                holding.bookUrl(),
                holding.coverImgUrl(),
                holding.availabilityStatus(),
                holding.availabilityStatus() == AvailabilityStatus.AVAILABLE,
                userId == null ? null : favoriteLibraryIds.contains(holding.libraryId())
            )).toList(),
            relatedBooks(book)
        );
    }

    @Transactional(readOnly = true)
    public BookSearchResponse getBook(Long bookOriginId) {
        // 도서 상세 조회
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

        Map<Long, List<BookPlatformSummaryResponse>> libraryPlatformsByBookOriginId = groupPlatformSummaries(bookRepository.findLibraryPlatformSummaries(bookOriginIds));
        Map<Long, List<BookPlatformSummaryResponse>> subscriptionPlatformsByBookOriginId = groupPlatformSummaries(bookRepository.findSubscriptionPlatformSummaries(bookOriginIds));

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

    private Page<BookOrigin> searchPlatform(
        String keyword,
        BookSearchType searchType,
        List<Long> categoryIds,
        Platform platform,
        PlatformType platformType,
        Pageable pageable
    ) {
        BookSearchCondition condition = new BookSearchCondition(
            keyword,
            searchType,
            categoryIds,
            List.of(),
            platformType == PlatformType.LIBRARY ? List.of(platform.getId()) : List.of(),
            platformType == PlatformType.SUBSCRIPTION ? List.of(platform.getId()) : List.of(),
            platform.getId()
        );
        return bookRepository.searchBooks(condition, pageable);
    }

    private List<PlatformBookSearchResponse.Item> toSearchItems(
        List<BookOrigin> books,
        PlatformType platformType,
        Long userId
    ) {
        List<Long> bookIds = books.stream().map(BookOrigin::getId).toList();
        Map<Long, List<BookLibraryHolding>> holdings = bookRepository
            .findAllLibraryHoldings(bookIds)
            .stream()
            .collect(Collectors.groupingBy(BookLibraryHolding::bookOriginId));
        Map<Long, BookSubscriptionAsset> assets = bookRepository
            .findSubscriptionAssets(bookIds)
            .stream()
            .collect(Collectors.toMap(BookSubscriptionAsset::bookOriginId, Function.identity()));
        Set<Long> favoriteBookIds = favoriteBookIds(userId);

        return books.stream().map(book -> {
            List<BookLibraryHolding> bookHoldings = holdings.getOrDefault(book.getId(), List.of());
            long availableCount = bookHoldings.stream()
                .filter(holding -> holding.availabilityStatus() == AvailabilityStatus.AVAILABLE)
                .count();
            BookSubscriptionAsset asset = assets.get(book.getId());
            return new PlatformBookSearchResponse.Item(
                book.getPlatformBookId(),
                book.getId(),
                book.getContentType(),
                book.getTitleRaw(),
                book.getAuthorRaw(),
                book.getPublisherRaw(),
                book.getPublishedDate(),
                book.getIsbn13Raw(),
                asset != null ? asset.coverImgUrl() : book.getCoverImgUrl(),
                asset != null ? asset.bookUrl() : book.getBookUrl(),
                platformType == PlatformType.LIBRARY ? bookHoldings.size() : 0,
                platformType == PlatformType.LIBRARY ? availableCount : 0,
                favoriteBookIds.contains(book.getId())
            );
        }).toList();
    }

    private List<PlatformBookDetailResponse.RelatedBook> relatedBooks(BookOrigin current) {
        Map<Long, MatchedBook> matches = new LinkedHashMap<>();
        if (hasText(current.getIsbn13Raw())) {
            bookRepository.findAllByIsbn13RawAndActiveTrueOrderByPlatform_IdAscIdAsc(current.getIsbn13Raw())
                .stream()
                .filter(candidate -> isOtherSupportedPlatform(current, candidate))
                .forEach(candidate -> matches.put(candidate.getId(), new MatchedBook(candidate, "ISBN")));
        }
        if (hasText(current.getTitleNorm()) && hasText(current.getAuthorNorm())) {
            bookRepository
                .findAllByTitleNormAndAuthorNormAndActiveTrueOrderByPlatform_IdAscIdAsc(
                    current.getTitleNorm(),
                    current.getAuthorNorm()
                )
                .stream()
                .filter(candidate -> isOtherSupportedPlatform(current, candidate))
                .filter(candidate -> !hasText(current.getIsbn13Raw()) || !hasText(candidate.getIsbn13Raw()))
                .forEach(candidate -> matches.putIfAbsent(
                    candidate.getId(),
                    new MatchedBook(candidate, "TITLE_AUTHOR")
                ));
        }

        return matches.values().stream().map(match -> {
            BookOrigin candidate = match.book();
            return new PlatformBookDetailResponse.RelatedBook(
                candidate.getPlatform().getId(),
                candidate.getPlatformBookId(),
                candidate.getPlatform().getPlatformName(),
                platformTypeOf(candidate.getPlatform()).name(),
                candidate.getTitleRaw(),
                candidate.getAuthorRaw(),
                match.matchType()
            );
        }).toList();
    }

    private boolean isOtherSupportedPlatform(BookOrigin current, BookOrigin candidate) {
        return !candidate.getPlatform().getId().equals(current.getPlatform().getId())
            && platformTypeOf(candidate.getPlatform()) != null;
    }

    private Set<Long> favoriteBookIds(Long userId) {
        return userId == null
            ? Set.of()
            : new LinkedHashSet<>(favoriteBookRepository.findBookOriginIdsByUserId(userId));
    }

    private Set<Long> favoriteLibraryIds(Long userId) {
        return userId == null
            ? Set.of()
            : new LinkedHashSet<>(favoriteLibraryRepository.findLibraryIdsByUserId(userId));
    }

    private BookSearchType strictSearchType(String searchType) {
        if (searchType == null || searchType.isBlank()) {
            return BookSearchType.ALL;
        }
        BookSearchType resolved = BookSearchType.from(searchType);
        if (resolved == BookSearchType.ALL) {
            throw new BookException(BookErrorCode.INVALID_PLATFORM_BOOK_REQUEST);
        }
        return resolved;
    }

    private PlatformType platformTypeOf(Platform platform) {
        if (platform.isSupportsSubscription()) {
            return PlatformType.SUBSCRIPTION;
        }
        return platform.isSupportsLibrary() ? PlatformType.LIBRARY : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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

    private enum PlatformType {
        ALL,
        LIBRARY,
        SUBSCRIPTION;

        private static PlatformType from(String value) {
            if (value == null) {
                throw new BookException(BookErrorCode.INVALID_PLATFORM_BOOK_REQUEST);
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BookException(BookErrorCode.INVALID_PLATFORM_BOOK_REQUEST);
            }
        }

        private boolean matches(PlatformType actual) {
            return actual != null && (this == ALL || this == actual);
        }
    }

    private record MatchedBook(BookOrigin book, String matchType) {
    }
}
