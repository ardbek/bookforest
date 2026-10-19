package com.ebook.bookforest.domain.book.service;

import com.ebook.bookforest.domain.book.dto.BookLibraryHoldingResponse;
import com.ebook.bookforest.domain.book.dto.BookLibraryPlatformHoldingResponse;
import com.ebook.bookforest.domain.book.dto.BookPlatformSummaryResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchPageResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchRequest;
import com.ebook.bookforest.domain.book.dto.BookSearchResponse;
import com.ebook.bookforest.domain.book.dto.BookSubscriptionPlatformResponse;
import com.ebook.bookforest.domain.book.dto.PlatformBookDetailResponse;
import com.ebook.bookforest.domain.book.dto.PlatformBookSearchRequest;
import com.ebook.bookforest.domain.book.dto.PlatformBookSearchResponse;
import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;
import com.ebook.bookforest.domain.book.enums.BookSearchType;
import com.ebook.bookforest.domain.book.enums.PlatformType;
import com.ebook.bookforest.domain.book.exception.BookErrorCode;
import com.ebook.bookforest.domain.book.exception.BookException;
import com.ebook.bookforest.domain.book.repository.BookCategorySummary;
import com.ebook.bookforest.domain.book.repository.BookLibraryHolding;
import com.ebook.bookforest.domain.book.repository.BookPlatformSummary;
import com.ebook.bookforest.domain.book.repository.BookRepository;
import com.ebook.bookforest.domain.book.repository.BookSearchCondition;
import com.ebook.bookforest.domain.book.repository.BookSubscriptionAsset;
import com.ebook.bookforest.domain.platform.dto.PlatformInfo;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.domain.platform.service.PlatformService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

/**
 * 도서 검색 서비스
 *
 * <p>검색 방식
 * <ul>
 *   <li>통합 검색: 모든 플랫폼의 원본 도서를 한 목록으로 검색</li>
 *   <li>플랫폼별 검색: 플랫폼마다 그룹을 나눠 검색, 도서관 보유 수와 즐겨찾기 표시 포함</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BookSearchService {

    /** 여러 플랫폼을 함께 검색할 때 플랫폼마다 보여 주는 미리보기 건수 */
    private static final int PREVIEW_SIZE = 3;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String MATCH_BY_ISBN = "ISBN";
    private static final String MATCH_BY_TITLE_AUTHOR = "TITLE_AUTHOR";

    private final BookRepository bookRepository;
    private final PlatformService platformService;

    /** 통합 검색(활성 도서, 제목순), 카테고리와 플랫폼 요약 포함 */
    @Transactional(readOnly = true)
    public BookSearchPageResponse searchBooks(BookSearchRequest request, Pageable pageable) {
        BookSearchCondition condition = new BookSearchCondition(
            request.keyword(),
            BookSearchType.from(request.searchType()),
            sanitizeIds(request.categoryIds()),
            sanitizeIds(request.libraryIds()),
            sanitizeIds(request.libraryPlatformIds()),
            sanitizeIds(request.subscriptionPlatformIds()),
            null
        );
        Page<BookOrigin> bookOrigins = bookRepository.searchBooks(condition, pageable);
        List<Long> bookOriginIds = bookOrigins.getContent().stream().map(BookOrigin::getId).toList();

        Map<Long, BookCategorySummary> categoriesByBookOriginId = findFirstCategories(bookOriginIds);
        Map<Long, List<BookPlatformSummaryResponse>> libraryPlatformsByBookOriginId =
            groupPlatformSummaries(bookRepository.findLibraryPlatformSummaries(bookOriginIds));
        Map<Long, List<BookPlatformSummaryResponse>> subscriptionPlatformsByBookOriginId =
            groupPlatformSummaries(bookRepository.findSubscriptionPlatformSummaries(bookOriginIds));

        return BookSearchPageResponse.from(bookOrigins.map(bookOrigin -> BookSearchResponse.from(
            bookOrigin,
            categoriesByBookOriginId.get(bookOrigin.getId()),
            libraryPlatformsByBookOriginId.getOrDefault(bookOrigin.getId(), List.of()),
            subscriptionPlatformsByBookOriginId.getOrDefault(bookOrigin.getId(), List.of()),
            false
        )));
    }

    /**
     * 도서 상세 조회(활성 도서)
     * 도서관 보유 현황은 이용 가능한 노출 도서관만 포함
     *
     * @throws BookException 도서가 없거나 비활성인 경우 BOOK_NOT_FOUND
     */
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
        List<BookSubscriptionPlatformResponse> subscriptionPlatformLinks =
            bookRepository.findSubscriptionPlatformLinks(bookOriginIds)
                .stream()
                .map(BookSubscriptionPlatformResponse::from)
                .toList();

        return BookSearchResponse.from(
            bookOrigin,
            category,
            libraryPlatforms,
            subscriptionPlatforms,
            subscriptionPlatformLinks,
            toLibraryPlatformHoldings(bookRepository.findLibraryHoldings(bookOriginIds)),
            false
        );
    }

    /**
     * 플랫폼별 검색
     *
     * <p>조회 범위
     * <ul>
     *   <li>platformId 지정: 해당 플랫폼 한 그룹을 요청한 페이지로 조회, 결과가 없어도 그룹 포함</li>
     *   <li>platformId 미지정: 조건에 맞는 플랫폼마다 미리보기 3건, 결과가 없는 플랫폼은 제외</li>
     * </ul>
     *
     * @param userId 로그인 사용자 ID, 없으면 즐겨찾기 표시 없음
     * @throws BookException 요청 값이 올바르지 않은 경우 INVALID_PLATFORM_BOOK_REQUEST,
     *                       플랫폼이 없는 경우 PLATFORM_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public PlatformBookSearchResponse searchPlatformBooks(PlatformBookSearchRequest request, Long userId) {
        validateSearchRequest(request);
        PlatformType requestedType = PlatformType.fromRequest(request.platformType());
        BookSearchType searchType = strictSearchType(request.searchType());
        List<PlatformInfo> platforms = selectPlatforms(request, requestedType);

        boolean singlePlatform = request.platformId() != null;
        Pageable pageable = singlePlatform
            ? PageRequest.of(request.page(), request.size())
            : PageRequest.of(0, PREVIEW_SIZE);
        Set<Long> favoriteBookIds = findFavoriteBookIds(userId);

        List<PlatformBookSearchResponse.Group> groups = new ArrayList<>();
        for (PlatformInfo platform : platforms) {
            PlatformType platformType = PlatformType.of(platform.supportsLibrary(), platform.supportsSubscription());
            Page<BookOrigin> books = searchPlatform(request, searchType, platform, platformType, pageable);
            if (singlePlatform || !books.isEmpty()) {
                groups.add(toGroup(platform, platformType, books, favoriteBookIds));
            }
        }

        long totalElements = groups.stream().mapToLong(PlatformBookSearchResponse.Group::totalElements).sum();
        return new PlatformBookSearchResponse(totalElements, groups);
    }

    /**
     * 플랫폼 도서 상세 조회(활성 도서)
     * 도서관 플랫폼 도서는 해당 플랫폼의 모든 이용 상태 보유 현황, 구독 플랫폼 도서는 구독 링크와 표지 사용
     *
     * @param userId 로그인 사용자 ID, 없으면 도서 즐겨찾기 false, 도서관 즐겨찾기 null
     * @throws BookException 플랫폼이 없는 경우 PLATFORM_NOT_FOUND,
     *                       도서가 없거나 도서관과 구독을 모두 지원하지 않는 플랫폼인 경우 PLATFORM_BOOK_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public PlatformBookDetailResponse getPlatformBook(Long platformId, String platformBookId, Long userId) {
        platformService.findPlatformInfo(platformId)
            .orElseThrow(() -> new BookException(BookErrorCode.PLATFORM_NOT_FOUND));
        BookOrigin book = bookRepository.findByPlatform_IdAndPlatformBookIdAndActiveTrue(platformId, platformBookId)
            .orElseThrow(() -> new BookException(BookErrorCode.PLATFORM_BOOK_NOT_FOUND));
        PlatformType platformType = platformTypeOf(book.getPlatform());
        if (platformType == null) {
            throw new BookException(BookErrorCode.PLATFORM_BOOK_NOT_FOUND);
        }

        List<BookLibraryHolding> holdings = platformType == PlatformType.LIBRARY
            ? bookRepository.findAllLibraryHoldings(List.of(book.getId()))
            : List.of();
        BookSubscriptionAsset asset = platformType == PlatformType.SUBSCRIPTION
            ? bookRepository.findSubscriptionAssets(List.of(book.getId())).stream().findFirst().orElse(null)
            : null;
        Set<Long> favoriteBookIds = findFavoriteBookIds(userId);
        Set<Long> favoriteLibraryIds = findFavoriteLibraryIds(userId);

        return new PlatformBookDetailResponse(
            book.getPlatform().getId(),
            book.getPlatform().getPlatformCode(),
            book.getPlatform().getPlatformName(),
            platformType.name(),
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
            countAvailable(holdings),
            favoriteBookIds.contains(book.getId()),
            book.getDescriptionRaw(),
            holdings.stream()
                .map(holding -> toLibraryItem(holding, userId, favoriteLibraryIds))
                .toList(),
            findRelatedBooks(book)
        );
    }


    private void validateSearchRequest(PlatformBookSearchRequest request) {
        boolean invalid = request.keyword() == null
            || request.keyword().isBlank()
            || request.page() < 0
            || request.size() < 1
            || request.size() > MAX_PAGE_SIZE;
        if (invalid) {
            throw new BookException(BookErrorCode.INVALID_PLATFORM_BOOK_REQUEST);
        }
    }

    /** 플랫폼별 검색은 전체 대상 검색을 허용하지 않아 값이 있으면 제목, 저자, 출판사 중 하나여야 함 */
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

    /** 검색할 플랫폼 결정, 요청한 플랫폼 ID 중 종류가 맞지 않는 것은 조용히 제외 */
    private List<PlatformInfo> selectPlatforms(PlatformBookSearchRequest request, PlatformType requestedType) {
        List<PlatformInfo> allPlatforms = platformService.getPlatformInfos();
        Set<Long> selectedIds = new LinkedHashSet<>(sanitizeIds(request.platformIds()));
        Set<Long> knownPlatformIds = allPlatforms.stream().map(PlatformInfo::id).collect(Collectors.toSet());
        if (!knownPlatformIds.containsAll(selectedIds)) {
            throw new BookException(BookErrorCode.PLATFORM_NOT_FOUND);
        }

        Set<Long> effectiveSelectedIds = allPlatforms.stream()
            .filter(platform -> requestedType.matches(typeOf(platform)))
            .map(PlatformInfo::id)
            .filter(selectedIds::contains)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (request.platformId() != null) {
            return List.of(selectSinglePlatform(request.platformId(), requestedType, effectiveSelectedIds));
        }
        return allPlatforms.stream()
            .filter(platform -> requestedType.matches(typeOf(platform)))
            .filter(platform -> effectiveSelectedIds.isEmpty() || effectiveSelectedIds.contains(platform.id()))
            .toList();
    }

    private PlatformInfo selectSinglePlatform(
        Long platformId,
        PlatformType requestedType,
        Set<Long> effectiveSelectedIds
    ) {
        PlatformInfo platform = platformService.findPlatformInfo(platformId)
            .orElseThrow(() -> new BookException(BookErrorCode.PLATFORM_NOT_FOUND));
        boolean typeMismatch = !requestedType.matches(typeOf(platform));
        boolean notSelected = !effectiveSelectedIds.isEmpty() && !effectiveSelectedIds.contains(platformId);
        if (typeMismatch || notSelected) {
            throw new BookException(BookErrorCode.INVALID_PLATFORM_BOOK_REQUEST);
        }
        return platform;
    }

    private Page<BookOrigin> searchPlatform(
        PlatformBookSearchRequest request,
        BookSearchType searchType,
        PlatformInfo platform,
        PlatformType platformType,
        Pageable pageable
    ) {
        BookSearchCondition condition = new BookSearchCondition(
            request.keyword().trim(),
            searchType,
            sanitizeIds(request.categoryIds()),
            List.of(),
            platformType == PlatformType.LIBRARY ? List.of(platform.id()) : List.of(),
            platformType == PlatformType.SUBSCRIPTION ? List.of(platform.id()) : List.of(),
            platform.id()
        );
        return bookRepository.searchBooks(condition, pageable);
    }

    private PlatformBookSearchResponse.Group toGroup(
        PlatformInfo platform,
        PlatformType platformType,
        Page<BookOrigin> books,
        Set<Long> favoriteBookIds
    ) {
        return new PlatformBookSearchResponse.Group(
            platform.id(),
            platform.platformCode(),
            platform.platformName(),
            platformType.name(),
            books.getTotalElements(),
            books.isLast(),
            toSearchItems(books.getContent(), platformType, favoriteBookIds)
        );
    }

    /** 도서관 플랫폼 도서만 보유 도서관 수를 채우고, 구독 도서는 구독 링크와 표지 사용 */
    private List<PlatformBookSearchResponse.Item> toSearchItems(
        List<BookOrigin> books,
        PlatformType platformType,
        Set<Long> favoriteBookIds
    ) {
        List<Long> bookIds = books.stream().map(BookOrigin::getId).toList();
        Map<Long, List<BookLibraryHolding>> holdingsByBookId = bookRepository.findAllLibraryHoldings(bookIds)
            .stream()
            .collect(Collectors.groupingBy(BookLibraryHolding::bookOriginId));
        Map<Long, BookSubscriptionAsset> assetsByBookId = bookRepository.findSubscriptionAssets(bookIds)
            .stream()
            .collect(Collectors.toMap(BookSubscriptionAsset::bookOriginId, Function.identity()));
        boolean library = platformType == PlatformType.LIBRARY;

        return books.stream().map(book -> {
            List<BookLibraryHolding> holdings = holdingsByBookId.getOrDefault(book.getId(), List.of());
            BookSubscriptionAsset asset = assetsByBookId.get(book.getId());
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
                library ? holdings.size() : 0,
                library ? countAvailable(holdings) : 0,
                favoriteBookIds.contains(book.getId())
            );
        }).toList();
    }

    private PlatformBookDetailResponse.LibraryItem toLibraryItem(
        BookLibraryHolding holding,
        Long userId,
        Set<Long> favoriteLibraryIds
    ) {
        return new PlatformBookDetailResponse.LibraryItem(
            holding.libraryId(),
            holding.libraryCode(),
            holding.libraryName(),
            holding.siteUrl(),
            holding.bookUrl(),
            holding.coverImgUrl(),
            holding.availabilityStatus(),
            holding.availabilityStatus() == AvailabilityStatus.AVAILABLE,
            userId == null ? null : favoriteLibraryIds.contains(holding.libraryId())
        );
    }

    /**
     * 다른 플랫폼의 같은 도서 조회
     * ISBN 일치를 먼저 찾고, 어느 한쪽에 ISBN이 없을 때만 정규화한 제목과 저자로 보완
     */
    private List<PlatformBookDetailResponse.RelatedBook> findRelatedBooks(BookOrigin current) {
        Map<Long, RelatedMatch> matches = new LinkedHashMap<>();

        if (hasText(current.getIsbn13Raw())) {
            bookRepository.findAllByIsbn13RawAndActiveTrueOrderByPlatform_IdAscIdAsc(current.getIsbn13Raw())
                .stream()
                .filter(candidate -> isOtherSupportedPlatform(current, candidate))
                .forEach(candidate -> matches.put(candidate.getId(), new RelatedMatch(candidate, MATCH_BY_ISBN)));
        }

        if (hasText(current.getTitleNorm()) && hasText(current.getAuthorNorm())) {
            bookRepository.findAllByTitleNormAndAuthorNormAndActiveTrueOrderByPlatform_IdAscIdAsc(
                    current.getTitleNorm(),
                    current.getAuthorNorm()
                )
                .stream()
                .filter(candidate -> isOtherSupportedPlatform(current, candidate))
                .filter(candidate -> !hasText(current.getIsbn13Raw()) || !hasText(candidate.getIsbn13Raw()))
                .forEach(candidate -> matches.putIfAbsent(
                    candidate.getId(),
                    new RelatedMatch(candidate, MATCH_BY_TITLE_AUTHOR)
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

    private Map<Long, BookCategorySummary> findFirstCategories(List<Long> bookOriginIds) {
        return bookRepository.findCategorySummaries(bookOriginIds)
            .stream()
            .collect(Collectors.toMap(
                BookCategorySummary::bookOriginId,
                Function.identity(),
                (first, ignored) -> first
            ));
    }

    private Map<Long, List<BookPlatformSummaryResponse>> groupPlatformSummaries(List<BookPlatformSummary> summaries) {
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

    /** 도서 상세의 도서관 보유 현황은 첫 보유 도서관의 플랫폼 하나로 묶음 */
    private List<BookLibraryPlatformHoldingResponse> toLibraryPlatformHoldings(List<BookLibraryHolding> holdings) {
        if (holdings.isEmpty()) {
            return List.of();
        }

        BookLibraryHolding first = holdings.get(0);
        return List.of(BookLibraryPlatformHoldingResponse.of(
            first.platformId(),
            first.platformCode(),
            first.platformName(),
            holdings.stream().map(BookLibraryHoldingResponse::from).toList()
        ));
    }

    private long countAvailable(List<BookLibraryHolding> holdings) {
        return holdings.stream()
            .filter(holding -> holding.availabilityStatus() == AvailabilityStatus.AVAILABLE)
            .count();
    }

    private Set<Long> findFavoriteBookIds(Long userId) {
        return userId == null ? Set.of() : bookRepository.findFavoriteBookOriginIds(userId);
    }

    private Set<Long> findFavoriteLibraryIds(Long userId) {
        return userId == null ? Set.of() : bookRepository.findFavoriteLibraryIds(userId);
    }

    private PlatformType typeOf(PlatformInfo platform) {
        return PlatformType.of(platform.supportsLibrary(), platform.supportsSubscription());
    }

    private PlatformType platformTypeOf(Platform platform) {
        return PlatformType.of(platform.isSupportsLibrary(), platform.isSupportsSubscription());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** null과 0 이하 ID를 제외하고 중복 제거 */
    private List<Long> sanitizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return ids.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .toList();
    }

    private record RelatedMatch(BookOrigin book, String matchType) {
    }
}
