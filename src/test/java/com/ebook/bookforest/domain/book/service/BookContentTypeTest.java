package com.ebook.bookforest.domain.book.service;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.dto.BookSearchResponse;
import com.ebook.bookforest.domain.book.enums.ContentType;
import com.ebook.bookforest.domain.book.repository.BookRepository;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.domain.platform.repository.PlatformRepository;
import com.ebook.bookforest.domain.user.repository.FavoriteBookRepository;
import com.ebook.bookforest.domain.user.repository.FavoriteLibraryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookContentTypeTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void identifiesPlatformTypeWithoutAvailableHoldings(boolean subscription) {
        var platform = Platform.builder().id(1L)
            .supportsSubscription(subscription).supportsLibrary(!subscription).build();
        var book = BookOrigin.builder().platform(platform).build();
        var response = BookSearchResponse.from(book, null, List.of(), List.of(), false);

        assertEquals(subscription ? "SUBSCRIPTION" : "LIBRARY", response.platformType());
        assertEquals(0, response.libraryCount());
        assertEquals(0, response.subscriptionPlatformCount());
    }

    @ParameterizedTest
    @EnumSource(ContentType.class)
    void preservesStoredContentTypeInSearchAndDetails(ContentType contentType) {
        var platform = Platform.builder().id(1L).supportsLibrary(true).build();
        var book = BookOrigin.builder().id(2L).platform(platform)
            .platformBookId("source").titleRaw("도서").contentType(contentType).build();
        var books = mock(BookRepository.class);
        var platforms = mock(PlatformRepository.class);
        var service = new BookSearchService(books, platforms,
            mock(FavoriteBookRepository.class), mock(FavoriteLibraryRepository.class));
        when(platforms.findAllByOrderByPlatformNameAsc()).thenReturn(List.of(platform));
        when(platforms.findById(1L)).thenReturn(Optional.of(platform));
        when(books.findById(2L)).thenReturn(Optional.of(book));
        when(books.findByPlatform_IdAndPlatformBookIdAndActiveTrue(1L, "source"))
            .thenReturn(Optional.of(book));
        when(books.searchBooks(any(), any())).thenReturn(new PageImpl<>(List.of(book)));

        assertEquals(contentType, service.getBook(2L).contentType());
        assertEquals(contentType, service.getPlatformBook(1L, "source", null).contentType());
        assertEquals(contentType, service.searchPlatformBooks("도서", null, null, "ALL",
            null, null, 0, 20, null).groups().get(0).content().get(0).contentType());
        assertEquals(contentType, service.searchBooks("도서", null, null, null, null, null,
            PageRequest.of(0, 20)).books().content().get(0).contentType());
    }
}
