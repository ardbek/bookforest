package com.ebook.bookforest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ebook.bookforest.domain.book.dto.BookSearchResponse;
import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.entity.LibraryBook;
import com.ebook.bookforest.domain.book.entity.SubscriptionBook;
import com.ebook.bookforest.domain.book.repository.BookRepository;
import com.ebook.bookforest.domain.book.repository.BookSearchCondition;
import com.ebook.bookforest.domain.book.service.BookSearchService;
import com.ebook.bookforest.domain.library.entity.Library;
import com.ebook.bookforest.domain.platform.entity.Platform;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
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
            List.of()
        );

        assertDoesNotThrow(() -> {
            bookRepository.searchBooks(condition, PageRequest.of(0, 20));
            bookRepository.findLibraryPlatformSummaries(List.of(1L));
            bookRepository.findSubscriptionPlatformSummaries(List.of(1L));
            bookRepository.findSubscriptionPlatformLinks(List.of(1L));
            bookRepository.findLibraryHoldings(List.of(1L));
            bookRepository.findCategorySummaries(List.of(1L));
        });
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

        assertEquals(platform.getId(), response.platformId());
        assertEquals(1, response.libraryCount());
        assertEquals(1, response.libraryPlatforms().size());
        assertEquals(1, response.subscriptionPlatforms().size());
        assertEquals("https://subscription.example/book-1", response.subscriptionPlatformLinks().get(0).bookUrl());
        assertEquals("https://library.example/book-1", response.libraryPlatformHoldings().get(0).libraries().get(0).bookUrl());
    }
}
