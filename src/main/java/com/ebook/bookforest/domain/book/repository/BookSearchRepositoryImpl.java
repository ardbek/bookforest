package com.ebook.bookforest.domain.book.repository;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;
import com.ebook.bookforest.domain.book.enums.BookSearchType;
import com.ebook.bookforest.domain.searchkeyword.service.SearchKeywordRules;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * 도서 검색 화면용 조회 구현
 * 검색 조건 조합이 많아 JPQL을 동적으로 조립
 */
@Repository
public class BookSearchRepositoryImpl implements BookSearchRepository {

    private final EntityManager entityManager;

    public BookSearchRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<BookOrigin> searchBooks(BookSearchCondition condition, Pageable pageable) {
        QueryParts queryParts = buildWhereClause(condition);

        String contentJpql = """
            select distinct bo
            from BookOrigin bo
            %s
            order by bo.titleRaw asc, bo.id asc
            """.formatted(queryParts.whereClause());

        TypedQuery<BookOrigin> contentQuery = entityManager.createQuery(contentJpql, BookOrigin.class);
        bindParameters(contentQuery, queryParts.parameters());
        contentQuery.setFirstResult((int) pageable.getOffset());
        contentQuery.setMaxResults(pageable.getPageSize());

        String countJpql = """
            select count(distinct bo.id)
            from BookOrigin bo
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
    public List<BookPlatformSummary> findLibraryPlatformSummaries(List<Long> bookOriginIds) {
        if (bookOriginIds == null || bookOriginIds.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                """
                    select new com.ebook.bookforest.domain.book.repository.BookPlatformSummary(
                        lb.bookOrigin.id,
                        p.id,
                        p.platformCode,
                        p.platformName,
                        count(distinct l.id)
                    )
                    from LibraryBook lb
                    join lb.library l
                    join l.platform p
                    where lb.bookOrigin.id in :bookOriginIds
                      and lb.bookOrigin.active = true
                      and lb.availabilityStatus = :available
                      and l.visible = true
                      and p.supportsLibrary = true
                    group by lb.bookOrigin.id, p.id, p.platformCode, p.platformName
                    order by p.platformName asc, p.id asc
                    """,
                BookPlatformSummary.class
            )
            .setParameter("bookOriginIds", bookOriginIds)
            .setParameter("available", AvailabilityStatus.AVAILABLE)
            .getResultList();
    }

    @Override
    public List<BookPlatformSummary> findSubscriptionPlatformSummaries(List<Long> bookOriginIds) {
        if (bookOriginIds == null || bookOriginIds.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                """
                    select new com.ebook.bookforest.domain.book.repository.BookPlatformSummary(
                        bo.id,
                        p.id,
                        p.platformCode,
                        p.platformName,
                        count(distinct sb.id)
                    )
                    from SubscriptionBook sb
                    join sb.bookOrigin bo
                    join bo.platform p
                    where bo.id in :bookOriginIds
                      and sb.availabilityStatus = :available
                      and bo.active = true
                      and p.supportsSubscription = true
                    group by bo.id, p.id, p.platformCode, p.platformName
                    order by p.platformName asc, p.id asc
                    """,
                BookPlatformSummary.class
            )
            .setParameter("bookOriginIds", bookOriginIds)
            .setParameter("available", AvailabilityStatus.AVAILABLE)
            .getResultList();
    }

    @Override
    public List<BookSubscriptionPlatformLink> findSubscriptionPlatformLinks(List<Long> bookOriginIds) {
        if (bookOriginIds == null || bookOriginIds.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                """
                    select new com.ebook.bookforest.domain.book.repository.BookSubscriptionPlatformLink(
                        bo.id,
                        p.id,
                        p.platformCode,
                        p.platformName,
                        coalesce(sb.bookUrl, bo.bookUrl)
                    )
                    from SubscriptionBook sb
                    join sb.bookOrigin bo
                    join bo.platform p
                    where bo.id in :bookOriginIds
                      and sb.availabilityStatus = :available
                      and bo.active = true
                      and p.supportsSubscription = true
                    order by p.platformName asc, p.id asc, sb.id asc
                    """,
                BookSubscriptionPlatformLink.class
            )
            .setParameter("bookOriginIds", bookOriginIds)
            .setParameter("available", AvailabilityStatus.AVAILABLE)
            .getResultList();
    }

    @Override
    public List<BookLibraryHolding> findLibraryHoldings(List<Long> bookOriginIds) {
        if (bookOriginIds == null || bookOriginIds.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                """
                    select new com.ebook.bookforest.domain.book.repository.BookLibraryHolding(
                        bo.id,
                        p.id,
                        p.platformCode,
                        p.platformName,
                        l.id,
                        l.libraryCode,
                        l.libraryName,
                        l.siteUrl,
                        coalesce(lb.bookUrl, bo.bookUrl),
                        coalesce(lb.coverImgUrl, bo.coverImgUrl),
                        lb.availabilityStatus
                    )
                    from LibraryBook lb
                    join lb.bookOrigin bo
                    join lb.library l
                    join l.platform p
                    where bo.id in :bookOriginIds
                      and bo.active = true
                      and lb.availabilityStatus = :available
                      and l.visible = true
                      and p.supportsLibrary = true
                    order by p.platformName asc, p.id asc, l.libraryName asc, l.id asc, lb.id asc
                    """,
                BookLibraryHolding.class
            )
            .setParameter("bookOriginIds", bookOriginIds)
            .setParameter("available", AvailabilityStatus.AVAILABLE)
            .getResultList();
    }

    @Override
    public List<BookLibraryHolding> findAllLibraryHoldings(List<Long> bookOriginIds) {
        if (bookOriginIds == null || bookOriginIds.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                """
                    select new com.ebook.bookforest.domain.book.repository.BookLibraryHolding(
                        bo.id,
                        p.id,
                        p.platformCode,
                        p.platformName,
                        l.id,
                        l.libraryCode,
                        l.libraryName,
                        l.siteUrl,
                        coalesce(lb.bookUrl, bo.bookUrl),
                        coalesce(lb.coverImgUrl, bo.coverImgUrl),
                        lb.availabilityStatus
                    )
                    from LibraryBook lb
                    join lb.bookOrigin bo
                    join lb.library l
                    join l.platform p
                    where bo.id in :bookOriginIds
                      and bo.active = true
                      and l.visible = true
                      and p.supportsLibrary = true
                      and p.id = bo.platform.id
                    order by l.libraryName asc, l.id asc, lb.id asc
                    """,
                BookLibraryHolding.class
            )
            .setParameter("bookOriginIds", bookOriginIds)
            .getResultList();
    }

    @Override
    public List<BookSubscriptionAsset> findSubscriptionAssets(List<Long> bookOriginIds) {
        if (bookOriginIds == null || bookOriginIds.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                """
                    select new com.ebook.bookforest.domain.book.repository.BookSubscriptionAsset(
                        bo.id,
                        coalesce(sb.bookUrl, bo.bookUrl),
                        coalesce(sb.coverImgUrl, bo.coverImgUrl)
                    )
                    from SubscriptionBook sb
                    join sb.bookOrigin bo
                    where bo.id in :bookOriginIds
                      and bo.active = true
                    """,
                BookSubscriptionAsset.class
            )
            .setParameter("bookOriginIds", bookOriginIds)
            .getResultList();
    }

    @Override
    public List<BookCategorySummary> findCategorySummaries(List<Long> bookOriginIds) {
        if (bookOriginIds == null || bookOriginIds.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                """
                    select new com.ebook.bookforest.domain.book.repository.BookCategorySummary(
                        boc.bookOrigin.id,
                        sc.id,
                        sc.categoryName,
                        sc.categoryPath
                    )
                    from BookOriginCategory boc
                    join boc.platformCategory pc
                    join pc.standardCategory sc
                    where boc.bookOrigin.id in :bookOriginIds
                      and pc.active = true
                      and sc.active = true
                    order by sc.sortOrder asc, sc.id asc
                    """,
                BookCategorySummary.class
            )
            .setParameter("bookOriginIds", bookOriginIds)
            .getResultList();
    }

    @Override
    public Set<Long> findFavoriteBookOriginIds(Long userId) {
        return new LinkedHashSet<>(entityManager.createQuery(
                "select fb.bookOrigin.id from FavoriteBook fb where fb.user.id = :userId",
                Long.class
            )
            .setParameter("userId", userId)
            .getResultList());
    }

    @Override
    public Set<Long> findFavoriteLibraryIds(Long userId) {
        return new LinkedHashSet<>(entityManager.createQuery(
                "select fl.library.id from FavoriteLibrary fl where fl.user.id = :userId",
                Long.class
            )
            .setParameter("userId", userId)
            .getResultList());
    }

    private QueryParts buildWhereClause(BookSearchCondition condition) {
        List<String> clauses = new ArrayList<>();
        Map<String, Object> parameters = new HashMap<>();
        clauses.add("bo.active = true");

        if (condition.sourcePlatformId() != null) {
            clauses.add("bo.platform.id = :sourcePlatformId");
            parameters.put("sourcePlatformId", condition.sourcePlatformId());
        }

        String keyword = normalizeKeyword(condition.keyword());
        if (keyword != null) {
            addKeywordClause(clauses, parameters, condition.searchType(), keyword);
        }

        if (!isEmpty(condition.categoryIds())) {
            clauses.add("""
                exists (
                    select 1
                    from BookOriginCategory boc
                    join boc.platformCategory pc
                    join pc.standardCategory sc
                    where boc.bookOrigin = bo
                      and pc.active = true
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
                    join sb.bookOrigin sbo
                    join sbo.platform sp
                    where sbo = bo
                      and sb.availabilityStatus = :availableStatus
                      and sbo.active = true
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
            case TITLE -> clauses.add("lower(bo.titleNorm) like :keyword");
            case AUTHOR -> clauses.add("lower(bo.authorNorm) like :keyword");
            case PUBLISHER -> clauses.add("lower(bo.publisherNorm) like :keyword");
            case ALL -> {
                // ngram 토큰(2글자)보다 짧은 한 글자 키워드와, 불리언 모드 구문을 깨는 큰따옴표가 든 키워드는 LIKE만 사용
                String fullTextClause = keyword.codePointCount(0, keyword.length()) > 1
                    && !keyword.contains("\"")
                    ? "full_text_match(bo.titleNorm, bo.authorNorm, "
                        + "bo.publisherNorm, :fullTextKeyword) and "
                    : "";
                clauses.add(fullTextClause + """
                    (
                        lower(bo.titleNorm) like :keyword
                        or lower(bo.authorNorm) like :keyword
                        or lower(bo.publisherNorm) like :keyword
                    )
                    """);
                if (!fullTextClause.isEmpty()) {
                    parameters.put("fullTextKeyword", "+\"" + keyword + "\"");
                }
            }
        }
        parameters.put("keyword", keywordPattern);
    }

    private String buildLibraryExistsClause(
        BookSearchCondition condition,
        Map<String, Object> parameters
    ) {
        List<String> filters = new ArrayList<>();
        filters.add("lb.bookOrigin = bo");
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
        return SearchKeywordRules.normalize(keyword);
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
