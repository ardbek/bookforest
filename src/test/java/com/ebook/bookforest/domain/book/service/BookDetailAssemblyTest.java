package com.ebook.bookforest.domain.book.service;

import com.ebook.bookforest.domain.platform.service.PlatformService;
import static com.ebook.bookforest.support.EntityIds.withId;
import com.ebook.bookforest.domain.book.dto.BookPlatformSummaryResponse;
import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;
import com.ebook.bookforest.domain.book.repository.BookCategorySummary;
import com.ebook.bookforest.domain.book.repository.BookLibraryHolding;
import com.ebook.bookforest.domain.book.repository.BookPlatformSummary;
import com.ebook.bookforest.domain.book.repository.BookRepository;
import com.ebook.bookforest.domain.book.repository.BookSubscriptionPlatformLink;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.domain.platform.repository.PlatformRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class BookDetailAssemblyTest {
    private final BookRepository books = mock(BookRepository.class);
    private final BookSearchService service = new BookSearchService(
        books,
        new PlatformService(mock(PlatformRepository.class))
    );
    private final Platform library = withId(Platform.builder().platformCode("lib")
        .platformName("도서관").supportsLibrary(true).build(), 1L);
    private final BookOrigin book = withId(BookOrigin.builder().platform(library).platformBookId("source")
        .titleRaw("도서").titleNorm("도서").authorRaw("저자").build(), 5L);

    @Test
    void detailKeepsCategorySummaryPlatformCountsAndSubscriptionLinkOrder() {
        given(books.findById(5L)).willReturn(Optional.of(book));
        given(books.findCategorySummaries(List.of(5L))).willReturn(List.of(
            new BookCategorySummary(5L, 9L, "소설", "문학>소설"),
            new BookCategorySummary(5L, 8L, "무시되는 분류", "무시")));
        given(books.findLibraryPlatformSummaries(List.of(5L)))
            .willReturn(List.of(new BookPlatformSummary(5L, 1L, "lib", "도서관", 4)));
        given(books.findSubscriptionPlatformSummaries(List.of(5L)))
            .willReturn(List.of(new BookPlatformSummary(5L, 2L, "sub", "구독", 1)));
        given(books.findSubscriptionPlatformLinks(List.of(5L))).willReturn(List.of(
            new BookSubscriptionPlatformLink(5L, 3L, "sub3", "구독3", "/book3"),
            new BookSubscriptionPlatformLink(5L, 2L, "sub2", "구독2", "/book2")));

        var response = service.getBook(5L);

        assertThat(response.primaryCategoryId()).isEqualTo(9L);
        assertThat(response.primaryCategoryPath()).isEqualTo("문학>소설");
        assertThat(response.libraryCount()).isEqualTo(4);
        assertThat(response.libraryPlatformCount()).isEqualTo(1);
        assertThat(response.subscriptionPlatformCount()).isEqualTo(1);
        assertThat(response.subscriptionPlatformLinks().stream().map(link -> link.platformId()).toList())
            .isEqualTo(List.of(3L, 2L));
        assertThat(response.libraryPlatformHoldings()).isEmpty();
        assertThat(response.favorite()).isFalse();
    }

    @Test
    void detailGroupsAllHoldingsUnderFirstHoldingPlatform() {
        given(books.findById(5L)).willReturn(Optional.of(book));
        given(books.findLibraryHoldings(List.of(5L))).willReturn(List.of(
            holding(1L, "lib", "도서관", 11L, AvailabilityStatus.AVAILABLE),
            holding(2L, "other", "다른 플랫폼", 12L, AvailabilityStatus.UNKNOWN)));

        var response = service.getBook(5L);

        assertThat(response.libraryPlatformHoldings()).hasSize(1);
        var group = response.libraryPlatformHoldings().get(0);
        assertThat(group.platformId()).isEqualTo(1L);
        assertThat(group.platformCode()).isEqualTo("lib");
        assertThat(group.platformName()).isEqualTo("도서관");
        assertThat(group.libraryCount()).isEqualTo(2);
        assertThat(group.libraries().stream().map(item -> item.libraryId()).toList()).isEqualTo(List.of(11L, 12L));
        assertThat(group.libraries().get(0).available()).isTrue();
        assertThat(group.libraries().get(1).available()).isFalse();
    }

    @Test
    void detailWithoutSummariesKeepsEmptyListsAndNullCategory() {
        given(books.findById(5L)).willReturn(Optional.of(book));

        var response = service.getBook(5L);

        assertThat(response.primaryCategoryId()).isNull();
        assertThat(response.primaryCategoryName()).isNull();
        assertThat(response.libraryCount()).isEqualTo(0);
        assertThat(response.libraryPlatforms()).isEqualTo(List.<BookPlatformSummaryResponse>of());
        assertThat(response.subscriptionPlatforms()).isEqualTo(List.<BookPlatformSummaryResponse>of());
        assertThat(response.subscriptionPlatformLinks()).isEmpty();
        assertThat(response.libraryPlatformHoldings()).isEmpty();
    }

    private BookLibraryHolding holding(
        Long platformId,
        String platformCode,
        String platformName,
        Long libraryId,
        AvailabilityStatus status
    ) {
        return new BookLibraryHolding(5L, platformId, platformCode, platformName, libraryId,
            "library" + libraryId, "도서관" + libraryId, "/site", "/holding", "/cover", status);
    }
}
