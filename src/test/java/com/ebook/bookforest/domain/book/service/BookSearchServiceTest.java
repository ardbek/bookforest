package com.ebook.bookforest.domain.book.service;

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
import com.ebook.bookforest.domain.user.repository.FavoriteBookRepository;
import com.ebook.bookforest.domain.user.repository.FavoriteLibraryRepository;
import com.ebook.bookforest.domain.user.service.FavoriteLookupService;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookSearchServiceTest {
    private final BookRepository books = mock(BookRepository.class);
    private final PlatformRepository platforms = mock(PlatformRepository.class);
    private final FavoriteBookRepository favoriteBooks = mock(FavoriteBookRepository.class);
    private final FavoriteLibraryRepository favoriteLibraries = mock(FavoriteLibraryRepository.class);
    private final BookSearchService service = new BookSearchService(books);
    private final PlatformBookService platformService = new PlatformBookService(books, platforms,
        new FavoriteLookupService(favoriteBooks, favoriteLibraries));
    private final Platform library = Platform.builder().id(1L).platformCode("lib")
        .platformName("도서관").supportsLibrary(true).build();
    private final Platform subscription = Platform.builder().id(2L).platformCode("sub")
        .platformName("구독").supportsLibrary(true).supportsSubscription(true).build();

    @Test
    void legacySearchKeepsLenientTypeAndSanitizesAllIdFilters() {
        when(books.searchBooks(any(), any())).thenReturn(Page.empty());
        var ids = Arrays.asList(null, -1L, 0L, 2L, 2L, 1L);
        service.searchBooks(" 원문 ", "unknown", ids, ids, ids, ids, PageRequest.of(2, 7));

        var condition = ArgumentCaptor.forClass(BookSearchCondition.class);
        verify(books).searchBooks(condition.capture(), eq(PageRequest.of(2, 7)));
        assertEquals(new BookSearchCondition(" 원문 ", BookSearchType.ALL,
            List.of(2L, 1L), List.of(2L, 1L), List.of(2L, 1L), List.of(2L, 1L), null), condition.getValue());
        verifyNoInteractions(platforms, favoriteBooks, favoriteLibraries);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALL", "unknown", "전체"})
    void platformSearchRejectsExplicitAllAndUnknownSearchTypes(String searchType) {
        var error = assertThrows(BookException.class, () -> platformService.searchPlatformBooks(
            "도서", searchType, null, "ALL", null, null, 0, 20, null));
        assertEquals(BookErrorCode.INVALID_PLATFORM_BOOK_REQUEST, error.getErrorCode());
        verifyNoInteractions(books, platforms, favoriteBooks, favoriteLibraries);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void platformSearchAllowsOmittedSearchType(String searchType) {
        when(platforms.findAllByOrderByPlatformNameAsc()).thenReturn(List.of(library));
        when(books.searchBooks(any(), any())).thenReturn(Page.empty());
        platformService.searchPlatformBooks(" 도서 ", searchType, null, " library ", null, null, 0, 20, null);
        verify(books).searchBooks(new BookSearchCondition("도서", BookSearchType.ALL,
            List.of(), List.of(), List.of(1L), List.of(), 1L), PageRequest.of(0, 3));
    }

    @Test
    void platformSearchKeepsRequestValidationAndMissingPlatformErrors() {
        for (int[] page : List.of(new int[]{-1, 20}, new int[]{0, 0}, new int[]{0, 101})) {
            assertEquals(BookErrorCode.INVALID_PLATFORM_BOOK_REQUEST, assertThrows(BookException.class,
                () -> platformService.searchPlatformBooks("도서", null, null, "ALL", null, null,
                    page[0], page[1], null)).getErrorCode());
        }
        for (String keyword : Arrays.asList(null, "", " ")) {
            assertEquals(BookErrorCode.INVALID_PLATFORM_BOOK_REQUEST, assertThrows(BookException.class,
                () -> platformService.searchPlatformBooks(keyword, null, null, "ALL", null, null, 0, 20, null)).getErrorCode());
        }
        for (String type : Arrays.asList(null, "", "unknown")) {
            assertEquals(BookErrorCode.INVALID_PLATFORM_BOOK_REQUEST, assertThrows(BookException.class,
                () -> platformService.searchPlatformBooks("도서", null, null, type, null, null, 0, 20, null)).getErrorCode());
        }
        when(platforms.findAllByOrderByPlatformNameAsc()).thenReturn(List.of(library, subscription));
        assertEquals(BookErrorCode.PLATFORM_NOT_FOUND, assertThrows(BookException.class,
            () -> platformService.searchPlatformBooks("도서", null, null, "ALL", List.of(99L), null, 0, 20, null)).getErrorCode());
        when(platforms.findById(2L)).thenReturn(Optional.of(subscription));
        assertEquals(BookErrorCode.INVALID_PLATFORM_BOOK_REQUEST, assertThrows(BookException.class,
            () -> platformService.searchPlatformBooks("도서", null, null, "LIBRARY", null, 2L, 0, 20, null)).getErrorCode());
        assertEquals(BookErrorCode.INVALID_PLATFORM_BOOK_REQUEST, assertThrows(BookException.class,
            () -> platformService.searchPlatformBooks("도서", null, null, "ALL", List.of(1L), 2L, 0, 20, null)).getErrorCode());
        assertEquals(BookErrorCode.PLATFORM_NOT_FOUND, assertThrows(BookException.class,
            () -> platformService.searchPlatformBooks("도서", null, null, "ALL", null, 99L, 0, 20, null)).getErrorCode());
        verifyNoInteractions(books);
    }

    @Test
    void groupedSearchPreservesPlatformOrderSubscriptionPriorityAndPreviewSize() {
        when(platforms.findAllByOrderByPlatformNameAsc()).thenReturn(List.of(subscription, library,
            Platform.builder().id(3L).build()));
        when(books.searchBooks(any(), any())).thenAnswer(call -> {
            BookSearchCondition condition = call.getArgument(0);
            Platform platform = condition.sourcePlatformId().equals(2L) ? subscription : library;
            return new PageImpl<>(List.of(book(platform.getId() * 10, platform, null)), call.getArgument(1), 5);
        });
        var response = platformService.searchPlatformBooks(" 도서 ", "제목", Arrays.asList(null, 0L, 4L, 4L),
            "ALL", Arrays.asList(null, -1L, 1L, 2L, 2L), null, 5, 80, null);

        assertEquals(10, response.totalElements());
        assertEquals(List.of(2L, 1L), response.groups().stream().map(group -> group.platformId()).toList());
        assertEquals("SUBSCRIPTION", response.groups().get(0).platformType());
        assertFalse(response.groups().get(0).last());
        var conditions = ArgumentCaptor.forClass(BookSearchCondition.class);
        verify(books, times(2)).searchBooks(conditions.capture(), eq(PageRequest.of(0, 3)));
        assertEquals(new BookSearchCondition("도서", BookSearchType.TITLE, List.of(4L), List.of(),
            List.of(), List.of(2L), 2L), conditions.getAllValues().get(0));
        verifyNoInteractions(favoriteBooks, favoriteLibraries);
    }

    @Test
    void incompatibleSelectedTypeStillFallsBackToAllPlatformsOfRequestedType() {
        when(platforms.findAllByOrderByPlatformNameAsc()).thenReturn(List.of(library, subscription));
        when(books.searchBooks(any(), any())).thenReturn(Page.empty());
        assertTrue(platformService.searchPlatformBooks("도서", null, null, "LIBRARY", List.of(2L),
            null, 0, 20, null).groups().isEmpty());
        verify(books).searchBooks(new BookSearchCondition("도서", BookSearchType.ALL, List.of(),
            List.of(), List.of(1L), List.of(), 1L), PageRequest.of(0, 3));
    }

    @Test
    void explicitPlatformKeepsEmptyGroupAndRequestedPaging() {
        when(platforms.findAllByOrderByPlatformNameAsc()).thenReturn(List.of(library));
        when(platforms.findById(1L)).thenReturn(Optional.of(library));
        when(books.searchBooks(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(9, 2), 4));
        var response = platformService.searchPlatformBooks("도서", null, null, "ALL", null, 1L, 9, 2, null);
        assertEquals(4, response.totalElements());
        assertEquals(1, response.groups().size());
        assertTrue(response.groups().get(0).content().isEmpty());
        assertTrue(response.groups().get(0).last());
        verify(books).searchBooks(any(), eq(PageRequest.of(9, 2)));
    }

    @Test
    void detailsPreserveAvailabilityAnonymousNullAndAuthenticatedFavorites() {
        var current = book(10L, library, null);
        when(platforms.findById(1L)).thenReturn(Optional.of(library));
        when(books.findByPlatform_IdAndPlatformBookIdAndActiveTrue(1L, "source10"))
            .thenReturn(Optional.of(current));
        when(books.findAllLibraryHoldings(List.of(10L))).thenReturn(List.of(
            holding(11L, AvailabilityStatus.AVAILABLE), holding(12L, AvailabilityStatus.UNKNOWN)));
        var anonymous = platformService.getPlatformBook(1L, "source10", null);
        assertEquals(2, anonymous.libraryCount());
        assertEquals(1, anonymous.availableLibraryCount());
        assertFalse(anonymous.favoriteYn());
        assertTrue(anonymous.libraries().stream().allMatch(item -> item.favoriteYn() == null));
        verifyNoInteractions(favoriteBooks, favoriteLibraries);

        when(favoriteBooks.findBookOriginIdsByUserId(7L)).thenReturn(List.of(10L));
        when(favoriteLibraries.findLibraryIdsByUserId(7L)).thenReturn(List.of(11L));
        var authenticated = platformService.getPlatformBook(1L, "source10", 7L);
        assertTrue(authenticated.favoriteYn());
        assertEquals(Boolean.TRUE, authenticated.libraries().get(0).favoriteYn());
        assertEquals(Boolean.FALSE, authenticated.libraries().get(1).favoriteYn());
        assertEquals(anonymous.libraries().get(0).bookUrl(), authenticated.libraries().get(0).bookUrl());
    }

    @Test
    void subscriptionAssetAndRelatedMatchingKeepExistingFallbackAndOrder() {
        var current = book(20L, subscription, "9780000000001");
        var isbnMatch = book(10L, library, current.getIsbn13Raw());
        var titleMatch = book(11L, library, null);
        var otherIsbn = book(12L, library, "9780000000002");
        var unsupported = book(13L, Platform.builder().id(3L).build(), current.getIsbn13Raw());
        when(platforms.findById(2L)).thenReturn(Optional.of(subscription));
        when(books.findByPlatform_IdAndPlatformBookIdAndActiveTrue(2L, "source20"))
            .thenReturn(Optional.of(current));
        when(books.findSubscriptionAssets(List.of(20L))).thenReturn(List.of(new BookSubscriptionAsset(20L, "/asset", null)));
        when(books.findAllByIsbn13RawAndActiveTrueOrderByPlatform_IdAscIdAsc(current.getIsbn13Raw()))
            .thenReturn(List.of(current, isbnMatch, unsupported));
        when(books.findAllByTitleNormAndAuthorNormAndActiveTrueOrderByPlatform_IdAscIdAsc("제목", "저자"))
            .thenReturn(List.of(current, isbnMatch, titleMatch, otherIsbn));

        var response = platformService.getPlatformBook(2L, "source20", null);
        assertEquals("SUBSCRIPTION", response.platformType());
        assertEquals("/asset", response.bookUrl());
        assertNull(response.coverImgUrl());
        assertTrue(response.libraries().isEmpty());
        assertEquals(List.of("source10", "source11"), response.relatedBooks().stream().map(item -> item.sourceBookId()).toList());
        assertEquals(List.of("ISBN", "TITLE_AUTHOR"), response.relatedBooks().stream().map(item -> item.matchType()).toList());
        verify(books, never()).findAllLibraryHoldings(anyList());
    }

    @Test
    void detailsKeepDistinctMissingBookErrors() {
        assertEquals(BookErrorCode.BOOK_NOT_FOUND,
            assertThrows(BookException.class, () -> service.getBook(10L)).getErrorCode());
        when(books.findById(10L)).thenReturn(Optional.of(BookOrigin.builder().active(false).build()));
        assertEquals(BookErrorCode.BOOK_NOT_FOUND,
            assertThrows(BookException.class, () -> service.getBook(10L)).getErrorCode());
        assertEquals(BookErrorCode.PLATFORM_NOT_FOUND,
            assertThrows(BookException.class, () -> platformService.getPlatformBook(1L, "absent", null)).getErrorCode());
        when(platforms.findById(1L)).thenReturn(Optional.of(library));
        assertEquals(BookErrorCode.PLATFORM_BOOK_NOT_FOUND,
            assertThrows(BookException.class, () -> platformService.getPlatformBook(1L, "absent", null)).getErrorCode());
    }

    private BookOrigin book(Long id, Platform platform, String isbn) {
        return BookOrigin.builder().id(id).platform(platform).platformBookId("source" + id)
            .titleRaw("제목").titleNorm("제목").authorRaw("저자").authorNorm("저자")
            .isbn13Raw(isbn).bookUrl("/book").coverImgUrl("/cover").build();
    }

    private BookLibraryHolding holding(Long id, AvailabilityStatus status) {
        return new BookLibraryHolding(10L, 1L, "lib", "도서관", id, "library" + id,
            "도서관" + id, "/site", "/holding", "/holding-cover", status);
    }
}
