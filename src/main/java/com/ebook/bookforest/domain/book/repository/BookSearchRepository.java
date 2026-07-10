package com.ebook.bookforest.domain.book.repository;

import com.ebook.bookforest.domain.book.entity.Book;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookSearchRepository {

    Page<Book> searchBooks(BookSearchCondition condition, Pageable pageable);

    List<BookPlatformSummary> findLibraryPlatformSummaries(List<Long> bookIds);

    List<BookPlatformSummary> findSubscriptionPlatformSummaries(List<Long> bookIds);

    List<BookSubscriptionPlatformLink> findSubscriptionPlatformLinks(List<Long> bookIds);

    List<BookLibraryHolding> findLibraryHoldings(List<Long> bookIds);

    List<BookCategorySummary> findPrimaryCategorySummaries(List<Long> bookIds);
}
