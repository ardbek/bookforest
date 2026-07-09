package com.ebook.bookforest.domain.book.repository;

import com.ebook.bookforest.domain.book.entity.Book;
import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;
import com.ebook.bookforest.domain.book.enums.BookSearchType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class BookSearchRepositoryImpl implements BookSearchRepository {

    private final EntityManager entityManager;

    public BookSearchRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<Book> searchBooks(BookSearchCondition condition, Pageable pageable) {
        QueryParts queryParts = buildWhereClause(condition);

        String contentJpql = """
            select distinct b
            from Book b
            %s
            order by b.title asc, b.id asc
            """.formatted(queryParts.whereClause());

        TypedQuery<Book> contentQuery = entityManager.createQuery(contentJpql, Book.class);
        bindParameters(contentQuery, queryParts.parameters());
        contentQuery.setFirstResult((int) pageable.getOffset());
        contentQuery.setMaxResults(pageable.getPageSize());

        String countJpql = """
            select count(distinct b.id)
            from Book b
            %s
            """.formatted(queryParts.whereClause());
        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql, Long.class);
        bindParameters(countQuery, queryParts.parameters());

        return new PageImpl<>(
            contentQuery.getResultList(),
            pageable,
            countQuery.getSingleResult()
        );
    }

    @Override
    public List<BookPlatformSummary> findLibraryPlatformSummaries(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                """
                    select new com.ebook.bookforest.domain.book.repository.BookPlatformSummary(
                        lb.book.id,
                        p.id,
                        p.platformCode,
                        p.platformName,
                        count(distinct l.id)
                    )
                    from LibraryBook lb
                    join lb.library l
                    join l.platform p
                    where lb.book.id in :bookIds
                      and lb.availabilityStatus = :available
                      and l.visible = true
                      and p.supportsLibrary = true
                    group by lb.book.id, p.id, p.platformCode, p.platformName
                    order by p.platformName asc, p.id asc
                    """,
                BookPlatformSummary.class
            )
            .setParameter("bookIds", bookIds)
            .setParameter("available", AvailabilityStatus.AVAILABLE)
            .getResultList();
    }

    @Override
    public List<BookPlatformSummary> findSubscriptionPlatformSummaries(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                """
                    select new com.ebook.bookforest.domain.book.repository.BookPlatformSummary(
                        sb.book.id,
                        p.id,
                        p.platformCode,
                        p.platformName,
                        count(distinct sb.id)
                    )
                    from SubscriptionBook sb
                    join sb.bookOrigin bo
                    join bo.platform p
                    where sb.book.id in :bookIds
                      and sb.availabilityStatus = :available
                      and bo.active = true
                      and p.supportsSubscription = true
                    group by sb.book.id, p.id, p.platformCode, p.platformName
                    order by p.platformName asc, p.id asc
                    """,
                BookPlatformSummary.class
            )
            .setParameter("bookIds", bookIds)
            .setParameter("available", AvailabilityStatus.AVAILABLE)
            .getResultList();
    }

    @Override
    public List<BookSubscriptionPlatformLink> findSubscriptionPlatformLinks(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                """
                    select new com.ebook.bookforest.domain.book.repository.BookSubscriptionPlatformLink(
                        sb.book.id,
                        p.id,
                        p.platformCode,
                        p.platformName,
                        coalesce(sb.bookUrl, bo.bookUrl)
                    )
                    from SubscriptionBook sb
                    join sb.bookOrigin bo
                    join bo.platform p
                    where sb.book.id in :bookIds
                      and sb.availabilityStatus = :available
                      and bo.active = true
                      and p.supportsSubscription = true
                    order by p.platformName asc, p.id asc, sb.id asc
                    """,
                BookSubscriptionPlatformLink.class
            )
            .setParameter("bookIds", bookIds)
            .setParameter("available", AvailabilityStatus.AVAILABLE)
            .getResultList();
    }

    @Override
    public List<BookCategorySummary> findPrimaryCategorySummaries(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                """
                    select new com.ebook.bookforest.domain.book.repository.BookCategorySummary(
                        bc.book.id,
                        sc.id,
                        sc.categoryName,
                        sc.categoryPath
                    )
                    from BookCategory bc
                    join bc.standardCategory sc
                    where bc.book.id in :bookIds
                      and bc.primary = true
                      and sc.active = true
                    order by sc.sortOrder asc, sc.id asc
                    """,
                BookCategorySummary.class
            )
            .setParameter("bookIds", bookIds)
            .getResultList();
    }

    private QueryParts buildWhereClause(BookSearchCondition condition) {
        List<String> clauses = new ArrayList<>();
        Map<String, Object> parameters = new HashMap<>();

        String keyword = normalizeKeyword(condition.keyword());
        if (keyword != null) {
            addKeywordClause(clauses, parameters, condition.searchType(), keyword);
        }

        if (!isEmpty(condition.categoryIds())) {
            clauses.add("""
                exists (
                    select 1
                    from BookCategory bc
                    join bc.standardCategory sc
                    where bc.book = b
                      and sc.active = true
                      and sc.id in :categoryIds
                )
                """);
            parameters.put("categoryIds", condition.categoryIds());
        }

        List<String> sourceClauses = new ArrayList<>();
        if (!isEmpty(condition.libraryIds()) || !isEmpty(condition.libraryPlatformIds())) {
            sourceClauses.add(buildLibraryExistsClause(condition, parameters));
        }
        if (!isEmpty(condition.subscriptionPlatformIds())) {
            sourceClauses.add("""
                exists (
                    select 1
                    from SubscriptionBook sb
                    join sb.bookOrigin bo
                    join bo.platform sp
                    where sb.book = b
                      and sb.availabilityStatus = :availableStatus
                      and bo.active = true
                      and sp.supportsSubscription = true
                      and sp.id in :subscriptionPlatformIds
                )
                """);
            parameters.put("availableStatus", AvailabilityStatus.AVAILABLE);
            parameters.put("subscriptionPlatformIds", condition.subscriptionPlatformIds());
        }

        if (!sourceClauses.isEmpty()) {
            clauses.add("(" + String.join(" or ", sourceClauses) + ")");
        }

        if (clauses.isEmpty()) {
            return new QueryParts("", parameters);
        }
        return new QueryParts("where " + String.join(" and ", clauses), parameters);
    }

    private void addKeywordClause(
        List<String> clauses,
        Map<String, Object> parameters,
        BookSearchType searchType,
        String keyword
    ) {
        String keywordPattern = "%" + keyword + "%";
        BookSearchType resolvedSearchType = searchType == null ? BookSearchType.ALL : searchType;

        switch (resolvedSearchType) {
            case TITLE -> clauses.add("lower(b.titleNorm) like :keyword");
            case AUTHOR -> clauses.add("lower(b.authorNorm) like :keyword");
            case PUBLISHER -> clauses.add("lower(b.publisherNorm) like :keyword");
            case ALL -> clauses.add("""
                (
                    lower(b.titleNorm) like :keyword
                    or lower(b.authorNorm) like :keyword
                    or lower(b.publisherNorm) like :keyword
                )
                """);
        }
        parameters.put("keyword", keywordPattern);
    }

    private String buildLibraryExistsClause(
        BookSearchCondition condition,
        Map<String, Object> parameters
    ) {
        List<String> filters = new ArrayList<>();
        filters.add("lb.book = b");
        filters.add("lb.availabilityStatus = :availableStatus");
        filters.add("l.visible = true");
        filters.add("lp.supportsLibrary = true");

        parameters.put("availableStatus", AvailabilityStatus.AVAILABLE);

        if (!isEmpty(condition.libraryIds())) {
            filters.add("l.id in :libraryIds");
            parameters.put("libraryIds", condition.libraryIds());
        }

        if (!isEmpty(condition.libraryPlatformIds())) {
            filters.add("lp.id in :libraryPlatformIds");
            parameters.put("libraryPlatformIds", condition.libraryPlatformIds());
        }

        return """
            exists (
                select 1
                from LibraryBook lb
                join lb.library l
                join l.platform lp
                where %s
            )
            """.formatted(String.join(" and ", filters));
    }

    private <T> void bindParameters(TypedQuery<T> query, Map<String, Object> parameters) {
        parameters.forEach(query::setParameter);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isEmpty(List<Long> values) {
        return values == null || values.isEmpty();
    }

    private record QueryParts(
        String whereClause,
        Map<String, Object> parameters
    ) {
    }
}
