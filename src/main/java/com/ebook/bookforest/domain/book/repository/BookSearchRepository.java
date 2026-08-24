package com.ebook.bookforest.domain.book.repository;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookSearchRepository {

    Page<BookOrigin> searchBooks(BookSearchCondition condition, Pageable pageable);

    List<BookPlatformSummary> findLibraryPlatformSummaries(List<Long> bookOriginIds);

    List<BookPlatformSummary> findSubscriptionPlatformSummaries(List<Long> bookOriginIds);

    List<BookSubscriptionPlatformLink> findSubscriptionPlatformLinks(List<Long> bookOriginIds);

    List<BookLibraryHolding> findLibraryHoldings(List<Long> bookOriginIds);

    List<BookLibraryHolding> findAllLibraryHoldings(List<Long> bookOriginIds);

    List<BookSubscriptionAsset> findSubscriptionAssets(List<Long> bookOriginIds);

    List<BookCategorySummary> findCategorySummaries(List<Long> bookOriginIds);
}
