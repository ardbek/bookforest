package com.ebook.bookforest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.ebook.bookforest.domain.book.dto.PlatformBookSearchRequest;

import com.ebook.bookforest.domain.book.dto.BookSearchResponse;
import com.ebook.bookforest.domain.book.dto.PlatformBookDetailResponse;
import com.ebook.bookforest.domain.book.dto.PlatformBookSearchResponse;
import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.entity.LibraryBook;
import com.ebook.bookforest.domain.book.entity.SubscriptionBook;
import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;
import com.ebook.bookforest.domain.book.repository.BookRepository;
import com.ebook.bookforest.domain.book.repository.BookSearchCondition;
import com.ebook.bookforest.domain.book.service.BookSearchService;
import com.ebook.bookforest.domain.library.entity.Library;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.support.MySqlTestContainerConfig;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전체 애플리케이션 컨텍스트 로딩과 도서 조회 흐름 검증, 실제 MySQL 필요
 */
@SpringBootTest
@Import(MySqlTestContainerConfig.class)
@Tag("db")
class BookforestApplicationTests {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookSearchService bookSearchService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void bookOriginQueriesWork() {
        BookSearchCondition condition = new BookSearchCondition(
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null
        );

        assertThatCode(() -> {
            bookRepository.searchBooks(condition, PageRequest.of(0, 20));
            bookRepository.findLibraryPlatformSummaries(List.of(1L));
            bookRepository.findSubscriptionPlatformSummaries(List.of(1L));
            bookRepository.findSubscriptionPlatformLinks(List.of(1L));
            bookRepository.findLibraryHoldings(List.of(1L));
            bookRepository.findCategorySummaries(List.of(1L));
        }).doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void bookOriginDetailKeepsPlatformHoldingsAndLinks() {
        Platform platform = Platform.builder()
            .platformCode("test")
            .platformName("테스트 플랫폼")
            .supportsLibrary(true)
            .supportsSubscription(true)
            .build();
        entityManager.persist(platform);

        BookOrigin bookOrigin = BookOrigin.builder()
            .platform(platform)
            .platformBookId("book-1")
            .titleRaw("테스트 도서")
            .titleNorm("테스트도서")
            .build();
        entityManager.persist(bookOrigin);

        Library library = Library.builder()
            .platform(platform)
            .libraryCode("library-1")
            .libraryName("테스트 도서관")
            .build();
        entityManager.persist(library);

        entityManager.persist(LibraryBook.builder()
            .library(library)
            .bookOrigin(bookOrigin)
            .bookUrl("https://library.example/book-1")
            .build());
        entityManager.persist(SubscriptionBook.builder()
            .bookOrigin(bookOrigin)
            .bookUrl("https://subscription.example/book-1")
            .build());
        entityManager.flush();
        entityManager.clear();

        BookSearchResponse response = bookSearchService.getBook(bookOrigin.getId());

        assertThat(response.platformId()).isEqualTo(platform.getId());
        assertThat(response.libraryCount()).isEqualTo(1);
        assertThat(response.libraryPlatforms()).hasSize(1);
        assertThat(response.subscriptionPlatforms()).hasSize(1);
        assertThat(response.subscriptionPlatformLinks().get(0).bookUrl())
            .isEqualTo("https://subscription.example/book-1");
        assertThat(response.libraryPlatformHoldings().get(0).libraries().get(0).bookUrl())
            .isEqualTo("https://library.example/book-1");
    }

    @Test
    @Transactional
    void platformSearchAndDetailKeepSourceBooksSeparate() {
        Platform libraryPlatform = Platform.builder()
            .platformCode("contract-library")
            .platformName("계약 도서관")
            .supportsLibrary(true)
            .build();
        Platform subscriptionPlatform = Platform.builder()
            .platformCode("contract-subscription")
            .platformName("계약 구독")
            .supportsSubscription(true)
            .build();
        entityManager.persist(libraryPlatform);
        entityManager.persist(subscriptionPlatform);

        BookOrigin libraryBook = contractBook(libraryPlatform, "library-source", "9790000000001");
        BookOrigin secondLibraryBook = contractBook(libraryPlatform, "library-source-2", "9790000000002");
        BookOrigin subscriptionBook = contractBook(subscriptionPlatform, "subscription-source", "9790000000001");
        entityManager.persist(libraryBook);
        entityManager.persist(secondLibraryBook);
        entityManager.persist(subscriptionBook);

        Library firstLibrary = Library.builder()
            .platform(libraryPlatform)
            .libraryCode("contract-1")
            .libraryName("계약 도서관 1")
            .siteUrl("https://library.example")
            .build();
        Library secondLibrary = Library.builder()
            .platform(libraryPlatform)
            .libraryCode("contract-2")
            .libraryName("계약 도서관 2")
            .build();
        entityManager.persist(firstLibrary);
        entityManager.persist(secondLibrary);
        entityManager.persist(LibraryBook.builder()
            .library(firstLibrary)
            .bookOrigin(libraryBook)
            .bookUrl("https://library.example/library-source")
            .build());
        entityManager.persist(LibraryBook.builder()
            .library(secondLibrary)
            .bookOrigin(libraryBook)
            .availabilityStatus(AvailabilityStatus.UNKNOWN)
            .build());
        entityManager.persist(LibraryBook.builder()
            .library(firstLibrary)
            .bookOrigin(secondLibraryBook)
            .build());
        entityManager.persist(SubscriptionBook.builder()
            .bookOrigin(subscriptionBook)
            .bookUrl("https://subscription.example/subscription-source")
            .build());
        entityManager.flush();
        entityManager.clear();

        PlatformBookSearchResponse grouped = bookSearchService.searchPlatformBooks(new PlatformBookSearchRequest("계약검색도서",
            "TITLE",
            null,
            "ALL",
            null,
            null,
            0,
            20), null);
        assertThat(grouped.totalElements()).isEqualTo(3);
        assertThat(grouped.groups()).hasSize(2);
        assertThat(grouped.groups().stream().mapToLong(PlatformBookSearchResponse.Group::totalElements).sum())
            .isEqualTo(grouped.totalElements());
        assertThat(grouped.groups().stream().flatMap(group -> group.content().stream()))
            .noneMatch(PlatformBookSearchResponse.Item::favorite);

        PlatformBookSearchResponse firstPage = bookSearchService.searchPlatformBooks(new PlatformBookSearchRequest("계약검색도서", "TITLE", null, "LIBRARY", null, libraryPlatform.getId(), 0, 1), null);
        PlatformBookSearchResponse secondPage = bookSearchService.searchPlatformBooks(new PlatformBookSearchRequest("계약검색도서", "TITLE", null, "LIBRARY", null, libraryPlatform.getId(), 1, 1), null);
        assertThat(firstPage.groups().get(0).last()).isFalse();
        assertThat(secondPage.groups().get(0).last()).isTrue();
        assertThat(secondPage.groups().get(0).content().get(0).platformBookId())
            .isNotEqualTo(firstPage.groups().get(0).content().get(0).platformBookId());
        PlatformBookSearchResponse emptyPage = bookSearchService.searchPlatformBooks(new PlatformBookSearchRequest("계약검색도서", "TITLE", null, "LIBRARY", null, libraryPlatform.getId(), 9, 1), null);
        assertThat(emptyPage.totalElements()).isEqualTo(2);
        assertThat(emptyPage.groups().get(0).content()).isEmpty();

        PlatformBookDetailResponse detail = bookSearchService.getPlatformBook(
            libraryPlatform.getId(), "library-source", null
        );
        assertThat(detail.libraryCount()).isEqualTo(2);
        assertThat(detail.availableLibraryCount()).isEqualTo(1);
        assertThat(detail.libraries()).hasSize(2);
        assertThat(detail.relatedBooks().get(0).matchType()).isEqualTo("ISBN");

        PlatformBookDetailResponse subscriptionDetail = bookSearchService.getPlatformBook(
            subscriptionPlatform.getId(), "subscription-source", null
        );
        assertThat(subscriptionDetail.bookUrl()).isEqualTo("https://subscription.example/subscription-source");
        assertThat(subscriptionDetail.libraries()).isEmpty();
    }

    private BookOrigin contractBook(Platform platform, String sourceBookId, String isbn13) {
        return BookOrigin.builder()
            .platform(platform)
            .platformBookId(sourceBookId)
            .titleRaw("계약 검색 도서")
            .authorRaw("계약 저자")
            .titleNorm("계약검색도서")
            .authorNorm("계약저자")
            .isbn13Raw(isbn13)
            .build();
    }
}
