package com.ebook.bookforest.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebook.bookforest.domain.book.service.BookService;
import com.ebook.bookforest.domain.library.service.LibraryService;
import com.ebook.bookforest.domain.platform.service.PlatformService;
import com.ebook.bookforest.domain.user.dto.FavoriteBookPageResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteBookResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteLibraryPageResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteLibraryResponse;
import com.ebook.bookforest.global.exception.BusinessException;
import com.ebook.bookforest.support.MySqlDataJpaTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 즐겨찾기 도서, 도서관 관리 동작 검증
 * 내부 의존성이 바뀌어도 동작을 고정하도록 실제 Repository와 컨테이너 DB 사용
 * 준비 데이터는 엔티티 생성 방식과 무관하도록 SQL로 직접 입력
 */
@MySqlDataJpaTest
@Import({FavoriteService.class, BookService.class, LibraryService.class, PlatformService.class})
class FavoriteServiceIntegrationTest {

    private static final long USER_ID = 1001L;
    private static final long OTHER_USER_ID = 1002L;
    private static final long WITHDRAWN_USER_ID = 1003L;
    private static final long PLATFORM_ID = 2001L;
    private static final long BOOK_ID = 3001L;
    private static final long SECOND_BOOK_ID = 3002L;
    private static final long INACTIVE_BOOK_ID = 3003L;
    private static final long LIBRARY_ID = 4001L;
    private static final long SECOND_LIBRARY_ID = 4002L;
    private static final long HIDDEN_LIBRARY_ID = 4003L;
    private static final long MISSING_ID = 9999L;

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        insertUser(USER_ID, true);
        insertUser(OTHER_USER_ID, true);
        insertUser(WITHDRAWN_USER_ID, false);
        jdbcTemplate.update("""
            insert into platform (platform_id, platform_code, platform_name, supports_library, supports_subscription)
            values (?, 'YES24', '예스24', true, false)
            """, PLATFORM_ID);
        insertBook(BOOK_ID, "불편한 편의점", true);
        insertBook(SECOND_BOOK_ID, "아몬드", true);
        insertBook(INACTIVE_BOOK_ID, "판매 중지 도서", false);
        insertLibrary(LIBRARY_ID, "서울도서관", true);
        insertLibrary(SECOND_LIBRARY_ID, "부산도서관", true);
        insertLibrary(HIDDEN_LIBRARY_ID, "숨김 도서관", false);
    }

    @Test
    @DisplayName("즐겨찾기 도서 목록은 최근 추가순으로 사용자 본인 것만 페이지 단위로 반환")
    void getFavoriteBooksReturnsOwnBooksInRecentOrder() {
        // given
        insertFavoriteBook(USER_ID, BOOK_ID, "2026-10-01 10:00:00");
        insertFavoriteBook(USER_ID, SECOND_BOOK_ID, "2026-10-02 10:00:00");
        insertFavoriteBook(OTHER_USER_ID, BOOK_ID, "2026-10-03 10:00:00");

        // when
        FavoriteBookPageResponse response = favoriteService.getFavoriteBooks(USER_ID, PageRequest.of(0, 1));

        // then
        assertThat(response.favoriteBooks().content()).containsExactly(new FavoriteBookResponse(
            SECOND_BOOK_ID, PLATFORM_ID, "YES24", "예스24",
            "아몬드", "아몬드 저자", "아몬드 출판사", LocalDate.of(2021, 4, 20), "https://cover.example/3002"
        ));
        assertThat(response.favoriteBooks().totalElements()).isEqualTo(2);
        assertThat(response.favoriteBooks().last()).isFalse();
    }

    @Test
    @DisplayName("즐겨찾기 도서 ID 목록은 사용자 본인 것만 반환")
    void getFavoriteBookOriginIdsReturnsOwnIds() {
        // given
        insertFavoriteBook(USER_ID, BOOK_ID, "2026-10-01 10:00:00");
        insertFavoriteBook(OTHER_USER_ID, SECOND_BOOK_ID, "2026-10-01 10:00:00");

        // when
        List<Long> ids = favoriteService.getFavoriteBookOriginIds(USER_ID);

        // then
        assertThat(ids).containsExactly(BOOK_ID);
    }

    @Test
    @DisplayName("활성 도서를 즐겨찾기에 추가")
    void addFavoriteBookSavesFavorite() {
        // when
        favoriteService.addFavoriteBook(USER_ID, BOOK_ID);

        // then
        assertThat(favoriteBookIds(USER_ID)).containsExactly(BOOK_ID);
    }

    @Test
    @DisplayName("이미 즐겨찾기한 도서를 다시 추가하면 오류 없이 한 건만 유지")
    void addFavoriteBookIgnoresDuplicate() {
        // given
        favoriteService.addFavoriteBook(USER_ID, BOOK_ID);

        // when
        favoriteService.addFavoriteBook(USER_ID, BOOK_ID);

        // then
        assertThat(favoriteBookIds(USER_ID)).containsExactly(BOOK_ID);
    }

    @Test
    @DisplayName("없거나 비활성인 도서는 book_not_found 오류, 저장하지 않음")
    void addFavoriteBookRejectsMissingOrInactiveBook() {
        // when, then
        assertBusinessError(() -> favoriteService.addFavoriteBook(USER_ID, MISSING_ID),
            "book_not_found", HttpStatus.NOT_FOUND);
        assertBusinessError(() -> favoriteService.addFavoriteBook(USER_ID, INACTIVE_BOOK_ID),
            "book_not_found", HttpStatus.NOT_FOUND);
        assertThat(favoriteBookIds(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("즐겨찾기 도서 해제, 즐겨찾기하지 않은 도서 해제는 오류 없음")
    void removeFavoriteBookDeletesFavorite() {
        // given
        insertFavoriteBook(USER_ID, BOOK_ID, "2026-10-01 10:00:00");

        // when
        favoriteService.removeFavoriteBook(USER_ID, BOOK_ID);
        favoriteService.removeFavoriteBook(USER_ID, SECOND_BOOK_ID);
        entityManager.flush();

        // then
        assertThat(favoriteBookIds(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("즐겨찾기 도서관 목록은 최근 추가순으로 사용자 본인 것만 페이지 단위로 반환")
    void getFavoriteLibrariesReturnsOwnLibrariesInRecentOrder() {
        // given
        insertFavoriteLibrary(USER_ID, LIBRARY_ID, "2026-10-02 10:00:00");
        insertFavoriteLibrary(USER_ID, SECOND_LIBRARY_ID, "2026-10-01 10:00:00");
        insertFavoriteLibrary(OTHER_USER_ID, SECOND_LIBRARY_ID, "2026-10-03 10:00:00");

        // when
        FavoriteLibraryPageResponse response = favoriteService.getFavoriteLibraries(USER_ID, PageRequest.of(0, 8));

        // then
        assertThat(response.favoriteLibraries().content()).containsExactly(
            new FavoriteLibraryResponse(LIBRARY_ID, "서울도서관", "YES24", "예스24", "https://library.example/4001"),
            new FavoriteLibraryResponse(SECOND_LIBRARY_ID, "부산도서관", "YES24", "예스24", "https://library.example/4002")
        );
        assertThat(response.favoriteLibraries().totalElements()).isEqualTo(2);
        assertThat(response.favoriteLibraries().last()).isTrue();
    }

    @Test
    @DisplayName("즐겨찾기 도서관 ID 목록은 사용자 본인 것만 반환")
    void getFavoriteLibraryIdsReturnsOwnIds() {
        // given
        insertFavoriteLibrary(USER_ID, LIBRARY_ID, "2026-10-01 10:00:00");
        insertFavoriteLibrary(OTHER_USER_ID, SECOND_LIBRARY_ID, "2026-10-01 10:00:00");

        // when
        List<Long> ids = favoriteService.getFavoriteLibraryIds(USER_ID);

        // then
        assertThat(ids).containsExactly(LIBRARY_ID);
    }

    @Test
    @DisplayName("노출 중인 도서관을 즐겨찾기에 추가")
    void addFavoriteLibrarySavesFavorite() {
        // when
        favoriteService.addFavoriteLibrary(USER_ID, LIBRARY_ID);

        // then
        assertThat(favoriteLibraryIds(USER_ID)).containsExactly(LIBRARY_ID);
    }

    @Test
    @DisplayName("이미 즐겨찾기한 도서관을 다시 추가하면 오류 없이 한 건만 유지")
    void addFavoriteLibraryIgnoresDuplicate() {
        // given
        favoriteService.addFavoriteLibrary(USER_ID, LIBRARY_ID);

        // when
        favoriteService.addFavoriteLibrary(USER_ID, LIBRARY_ID);

        // then
        assertThat(favoriteLibraryIds(USER_ID)).containsExactly(LIBRARY_ID);
    }

    @Test
    @DisplayName("없거나 숨김인 도서관은 library_not_found 오류, 저장하지 않음")
    void addFavoriteLibraryRejectsMissingOrHiddenLibrary() {
        // when, then
        assertBusinessError(() -> favoriteService.addFavoriteLibrary(USER_ID, MISSING_ID),
            "library_not_found", HttpStatus.NOT_FOUND);
        assertBusinessError(() -> favoriteService.addFavoriteLibrary(USER_ID, HIDDEN_LIBRARY_ID),
            "library_not_found", HttpStatus.NOT_FOUND);
        assertThat(favoriteLibraryIds(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("즐겨찾기 도서관 해제, 즐겨찾기하지 않은 도서관 해제는 오류 없음")
    void removeFavoriteLibraryDeletesFavorite() {
        // given
        insertFavoriteLibrary(USER_ID, LIBRARY_ID, "2026-10-01 10:00:00");

        // when
        favoriteService.removeFavoriteLibrary(USER_ID, LIBRARY_ID);
        favoriteService.removeFavoriteLibrary(USER_ID, SECOND_LIBRARY_ID);
        entityManager.flush();

        // then
        assertThat(favoriteLibraryIds(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("탈퇴했거나 없는 사용자는 모든 즐겨찾기 기능에서 user_not_found 오류")
    void allOperationsRejectInactiveOrMissingUser() {
        for (long userId : new long[] {WITHDRAWN_USER_ID, MISSING_ID}) {
            assertBusinessError(() -> favoriteService.getFavoriteBooks(userId, PageRequest.of(0, 8)),
                "user_not_found", HttpStatus.NOT_FOUND);
            assertBusinessError(() -> favoriteService.getFavoriteBookOriginIds(userId),
                "user_not_found", HttpStatus.NOT_FOUND);
            assertBusinessError(() -> favoriteService.addFavoriteBook(userId, BOOK_ID),
                "user_not_found", HttpStatus.NOT_FOUND);
            assertBusinessError(() -> favoriteService.removeFavoriteBook(userId, BOOK_ID),
                "user_not_found", HttpStatus.NOT_FOUND);
            assertBusinessError(() -> favoriteService.getFavoriteLibraries(userId, PageRequest.of(0, 8)),
                "user_not_found", HttpStatus.NOT_FOUND);
            assertBusinessError(() -> favoriteService.getFavoriteLibraryIds(userId),
                "user_not_found", HttpStatus.NOT_FOUND);
            assertBusinessError(() -> favoriteService.addFavoriteLibrary(userId, LIBRARY_ID),
                "user_not_found", HttpStatus.NOT_FOUND);
            assertBusinessError(() -> favoriteService.removeFavoriteLibrary(userId, LIBRARY_ID),
                "user_not_found", HttpStatus.NOT_FOUND);
        }
    }

    private void assertBusinessError(ThrowingCallable call, String code, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode().code()).isEqualTo(code);
            assertThat(exception.getErrorCode().httpStatus()).isEqualTo(status);
        });
    }

    private void insertUser(long userId, boolean active) {
        jdbcTemplate.update(
            "insert into users (user_id, is_active, role) values (?, ?, 'ROLE_USER')",
            userId, active
        );
    }

    private void insertBook(long bookId, String title, boolean active) {
        jdbcTemplate.update("""
            insert into book_origin (
                book_origin_id, platform_id, platform_book_id, content_type, title_raw, title_norm,
                author_raw, publisher_raw, published_date, cover_img_url, is_active
            )
            values (?, ?, ?, 'EBOOK', ?, ?, ?, ?, '2021-04-20', ?, ?)
            """,
            bookId, PLATFORM_ID, "book-" + bookId, title, title,
            title + " 저자", title + " 출판사", "https://cover.example/" + bookId, active
        );
    }

    private void insertLibrary(long libraryId, String libraryName, boolean visible) {
        jdbcTemplate.update("""
            insert into library (library_id, platform_id, library_code, library_name, site_url, is_visible)
            values (?, ?, ?, ?, ?, ?)
            """,
            libraryId, PLATFORM_ID, "library-" + libraryId, libraryName,
            "https://library.example/" + libraryId, visible
        );
    }

    private void insertFavoriteBook(long userId, long bookId, String createdAt) {
        jdbcTemplate.update(
            "insert into favorite_book (user_id, book_origin_id, created_at) values (?, ?, ?)",
            userId, bookId, createdAt
        );
    }

    private void insertFavoriteLibrary(long userId, long libraryId, String createdAt) {
        jdbcTemplate.update(
            "insert into favorite_library (user_id, library_id, created_at) values (?, ?, ?)",
            userId, libraryId, createdAt
        );
    }

    private List<Long> favoriteBookIds(long userId) {
        return jdbcTemplate.queryForList(
            "select book_origin_id from favorite_book where user_id = ? order by book_origin_id",
            Long.class, userId
        );
    }

    private List<Long> favoriteLibraryIds(long userId) {
        return jdbcTemplate.queryForList(
            "select library_id from favorite_library where user_id = ? order by library_id",
            Long.class, userId
        );
    }
}
