package com.ebook.bookforest.domain.book.service;

import com.ebook.bookforest.domain.platform.service.PlatformService;
import com.ebook.bookforest.domain.book.dto.BookSearchRequest;
import com.ebook.bookforest.domain.book.dto.PlatformBookSearchRequest;
import static com.ebook.bookforest.support.EntityIds.withId;
import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.dto.BookSearchResponse;
import com.ebook.bookforest.domain.book.enums.ContentType;
import com.ebook.bookforest.domain.book.repository.BookRepository;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.domain.platform.repository.PlatformRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class BookContentTypeTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void identifiesPlatformTypeWithoutAvailableHoldings(boolean subscription) {
        var platform = withId(Platform.builder()
            .supportsSubscription(subscription).supportsLibrary(!subscription).build(), 1L);
        var book = BookOrigin.builder().platform(platform).platformBookId("source").titleRaw("도서").titleNorm("도서").build();
        var response = BookSearchResponse.from(book, null, List.of(), List.of(), false);

        assertThat(response.platformType()).isEqualTo(subscription ? "SUBSCRIPTION" : "LIBRARY");
        assertThat(response.libraryCount()).isEqualTo(0);
        assertThat(response.subscriptionPlatformCount()).isEqualTo(0);
    }

    @ParameterizedTest
    @EnumSource(ContentType.class)
    void preservesStoredContentTypeInSearchAndDetails(ContentType contentType) {
        var platform = withId(Platform.builder().supportsLibrary(true).build(), 1L);
        var book = withId(BookOrigin.builder().platform(platform)
            .platformBookId("source").titleRaw("도서").titleNorm("도서").contentType(contentType).build(), 2L);
        var books = mock(BookRepository.class);
        var platforms = mock(PlatformRepository.class);
        var service = new BookSearchService(books, new PlatformService(platforms));
        given(platforms.findAllByOrderByPlatformNameAsc()).willReturn(List.of(platform));
        given(platforms.findById(1L)).willReturn(Optional.of(platform));
        given(books.findById(2L)).willReturn(Optional.of(book));
        given(books.findByPlatform_IdAndPlatformBookIdAndActiveTrue(1L, "source"))
            .willReturn(Optional.of(book));
        given(books.searchBooks(any(), any())).willReturn(new PageImpl<>(List.of(book)));

        assertThat(service.getBook(2L).contentType()).isEqualTo(contentType);
        assertThat(service.getPlatformBook(1L, "source", null).contentType()).isEqualTo(contentType);
        var platformSearch = service.searchPlatformBooks(
            new PlatformBookSearchRequest("도서", null, null, "ALL", null, null, 0, 20),
            null
        );
        assertThat(platformSearch.groups().get(0).content().get(0).contentType()).isEqualTo(contentType);
        var bookSearch = service.searchBooks(
            new BookSearchRequest("도서", null, null, null, null, null),
            PageRequest.of(0, 20)
        );
        assertThat(bookSearch.books().content().get(0).contentType()).isEqualTo(contentType);
    }
}
