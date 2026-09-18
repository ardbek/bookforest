package com.ebook.bookforest.domain.book.service;

import static com.ebook.bookforest.domain.book.service.BookSearchFilters.sanitizeIds;

import com.ebook.bookforest.domain.book.dto.PlatformBookDetailResponse;
import com.ebook.bookforest.domain.book.dto.PlatformBookSearchResponse;
import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;
import com.ebook.bookforest.domain.book.enums.BookSearchType;
import com.ebook.bookforest.domain.book.exception.BookErrorCode;
import com.ebook.bookforest.domain.book.exception.BookException;
import com.ebook.bookforest.domain.book.repository.BookLibraryHolding;
import com.ebook.bookforest.domain.book.repository.BookRepository;
import com.ebook.bookforest.domain.book.repository.BookSearchCondition;
import com.ebook.bookforest.domain.book.repository.BookSubscriptionAsset;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.domain.platform.repository.PlatformRepository;
import com.ebook.bookforest.domain.user.service.FavoriteLookupService;
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
public class PlatformBookService {

    private final BookRepository bookRepository;
    private final PlatformRepository platformRepository;
    private final FavoriteLookupService favoriteLookupService;

    // 플랫폼별 도서를 검색하고 소장 정보와 즐겨찾기를 함께 반환한다.
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
        List<Platform> platforms = selectPlatforms(requestedType, platformIds, platformId);

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

    // 플랫폼의 원천 도서 상세와 관련 도서, 즐겨찾기를 조회한다.
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
        Set<Long> favoriteBookIds = favoriteLookupService.findBookOriginIds(userId);
        Set<Long> favoriteLibraryIds = favoriteLookupService.findLibraryIds(userId);
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

    // 요청한 유형과 선택 ID에 해당하는 플랫폼을 조회 순서대로 선택한다.
    private List<Platform> selectPlatforms(PlatformType requestedType, List<Long> platformIds, Long platformId) {
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

        return platforms;
    }

    // 플랫폼 하나에 해당하는 도서 검색 조건으로 조회한다.
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

    // 검색 도서에 소장 수와 구독 자산, 즐겨찾기를 결합한다.
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
        Set<Long> favoriteBookIds = favoriteLookupService.findBookOriginIds(userId);

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

    // 다른 플랫폼의 관련 도서를 ISBN 우선으로 찾는다.
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

    // 플랫폼 검색에서 지원하는 검색 유형만 허용한다.
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
