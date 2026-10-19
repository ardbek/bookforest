package com.ebook.bookforest.domain.book.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.enums.BookSearchType;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.support.MySqlDataJpaTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 도서 통합 검색의 FULLTEXT 조건 검증
 * InnoDB FULLTEXT는 커밋된 행만 검색하므로 테스트 트랜잭션 롤백을 끄고 데이터를 직접 커밋
 */
@MySqlDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BookSearchRepositoryFullTextTest {

    /** 이 테스트가 데이터를 넣고 지우는 테이블, 자식 테이블부터 정리 */
    private static final List<String> OWNED_TABLES = List.of("book_origin", "platform");

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        deleteOwnedTables();
    }

    @AfterEach
    void tearDown() {
        deleteOwnedTables();
    }

    @Test
    @DisplayName("커밋된 도서는 단어 중간의 두 글자 키워드로도 통합 검색 결과에 포함")
    void searchBooksFindsCommittedBookByNgramKeyword() {
        // given
        transactionTemplate.executeWithoutResult(status -> saveBook("자바의 정석", "자바의정석"));

        // when
        Page<BookOrigin> result = bookRepository.searchBooks(allTypeCondition("정석"), PageRequest.of(0, 20));

        // then
        assertThat(result.getContent())
            .extracting(BookOrigin::getTitleRaw)
            .containsExactly("자바의 정석");
    }

    @Test
    @DisplayName("커밋 전 도서는 같은 트랜잭션 안에서도 통합 검색 결과에서 제외")
    void searchBooksExcludesUncommittedBook() {
        // when
        List<BookOrigin> result = transactionTemplate.execute(status -> {
            saveBook("자바의 정석", "자바의정석");
            entityManager.flush();

            List<BookOrigin> books = bookRepository
                .searchBooks(allTypeCondition("정석"), PageRequest.of(0, 20))
                .getContent();

            status.setRollbackOnly();
            return books;
        });

        // then
        assertThat(result).isEmpty();
    }

    private void saveBook(String titleRaw, String titleNorm) {
        Platform platform = Platform.builder()
            .platformCode("fulltext-test")
            .platformName("검색 테스트 플랫폼")
            .supportsLibrary(true)
            .build();
        entityManager.persist(platform);

        entityManager.persist(BookOrigin.builder()
            .platform(platform)
            .platformBookId("fulltext-book-1")
            .titleRaw(titleRaw)
            .titleNorm(titleNorm)
            .build());
    }

    private BookSearchCondition allTypeCondition(String keyword) {
        return new BookSearchCondition(
            keyword,
            BookSearchType.ALL,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }

    private void deleteOwnedTables() {
        OWNED_TABLES.forEach(table -> jdbcTemplate.update("delete from " + table));
    }
}
