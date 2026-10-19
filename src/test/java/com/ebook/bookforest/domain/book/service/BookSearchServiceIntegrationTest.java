package com.ebook.bookforest.domain.book.service;

import com.ebook.bookforest.domain.book.dto.BookSearchRequest;
import com.ebook.bookforest.domain.book.dto.PlatformBookSearchRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.ebook.bookforest.domain.book.dto.BookPlatformSummaryResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchPageResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchResponse;
import com.ebook.bookforest.domain.book.dto.PlatformBookDetailResponse;
import com.ebook.bookforest.domain.book.dto.PlatformBookSearchResponse;
import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;
import com.ebook.bookforest.domain.book.enums.ContentType;
import com.ebook.bookforest.domain.platform.service.PlatformService;
import com.ebook.bookforest.global.exception.BusinessException;
import com.ebook.bookforest.support.MySqlDataJpaTest;
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
 * 도서 통합 검색, 플랫폼별 검색, 상세 조회 동작 검증
 * 리팩토링으로 플랫폼, 즐겨찾기 조회 경로가 바뀌어도 응답을 고정하도록 실제 DB 사용
 * 준비 데이터는 엔티티 생성 방식과 무관하도록 SQL로 입력, FULLTEXT를 쓰지 않는 제목 검색으로 검증
 */
@MySqlDataJpaTest
@Import({BookSearchService.class, PlatformService.class})
class BookSearchServiceIntegrationTest {

    private static final long KYOBO = 101L;
    private static final long YES24 = 102L;
    private static final long MILLIE = 103L;
    private static final long ETC = 104L;
    private static final long LIB_GARAM = 201L;
    private static final long LIB_NARAE = 202L;
    private static final long LIB_HIDDEN = 203L;
    private static final long LIB_YES = 204L;
    private static final long BOOK_KYOBO = 301L;
    private static final long BOOK_MILLIE = 302L;
    private static final long BOOK_YES = 303L;
    private static final long BOOK_INACTIVE = 304L;
    private static final long BOOK_ETC = 305L;
    private static final long USER_ID = 401L;
    private static final long CATEGORY_NOVEL = 501L;

    @Autowired
    private BookSearchService bookSearchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        insertPlatform(KYOBO, "KYOBO", "교보문고", true, false);
        insertPlatform(YES24, "YES24", "예스24", true, false);
        insertPlatform(MILLIE, "MILLIE", "밀리의서재", false, true);
        insertPlatform(ETC, "ETC", "기타", false, false);

        insertLibrary(LIB_GARAM, KYOBO, "가람도서관", true);
        insertLibrary(LIB_NARAE, KYOBO, "나래도서관", true);
        insertLibrary(LIB_HIDDEN, KYOBO, "숨김도서관", false);
        insertLibrary(LIB_YES, YES24, "예스도서관", true);

        insertBook(BOOK_KYOBO, KYOBO, "k-1", "자바의 정석", "9780000000001", true);
        insertBook(BOOK_MILLIE, MILLIE, "m-1", "자바의 정석", "9780000000001", true);
        insertBook(BOOK_YES, YES24, "y-1", "자바의 정석", null, true);
        insertBook(BOOK_INACTIVE, KYOBO, "k-2", "자바 판매 중지", null, false);
        insertBook(BOOK_ETC, ETC, "e-1", "자바 기타 도서", null, true);

        insertLibraryBook(BOOK_KYOBO, LIB_GARAM, "AVAILABLE");
        insertLibraryBook(BOOK_KYOBO, LIB_NARAE, "UNKNOWN");
        insertLibraryBook(BOOK_KYOBO, LIB_HIDDEN, "AVAILABLE");
        insertLibraryBook(BOOK_YES, LIB_YES, "AVAILABLE");
        jdbcTemplate.update("""
            insert into subscription_book (book_origin_id, availability_status, book_url, cover_img_url)
            values (?, 'AVAILABLE', 'https://millie.example/m-1', 'https://millie.example/m-1.jpg')
            """, BOOK_MILLIE);

        jdbcTemplate.update("""
            insert into standard_category (standard_category_id, category_code, category_name, category_path,
                depth, sort_order, is_active)
            values (?, 'NOVEL', '소설', '소설', 1, 0, true)
            """, CATEGORY_NOVEL);
        jdbcTemplate.update("""
            insert into platform_category (platform_category_id, platform_id, category_code, category_name,
                standard_category_id, mapping_status, depth, sort_order, is_active)
            values (601, ?, '31', '소설', ?, 'CONFIRMED', 1, 0, true)
            """, KYOBO, CATEGORY_NOVEL);
        jdbcTemplate.update(
            "insert into book_origin_category (book_origin_id, platform_category_id) values (?, 601)", BOOK_KYOBO
        );

        jdbcTemplate.update("insert into users (user_id, is_active, role) values (?, true, 'ROLE_USER')", USER_ID);
        jdbcTemplate.update("insert into favorite_book (user_id, book_origin_id) values (?, ?)", USER_ID, BOOK_KYOBO);
        jdbcTemplate.update("insert into favorite_library (user_id, library_id) values (?, ?)", USER_ID, LIB_GARAM);
    }

    @Test
    @DisplayName("도서관 플랫폼 도서 상세는 노출 도서관 보유 현황, 즐겨찾기, 다른 플랫폼 관련 도서를 반환")
    void getPlatformBookReturnsLibraryHoldingsFavoritesAndRelatedBooks() {
        // when
        PlatformBookDetailResponse detail = bookSearchService.getPlatformBook(KYOBO, "k-1", USER_ID);

        // then
        assertThat(detail.platformType()).isEqualTo("LIBRARY");
        assertThat(detail.canonicalBookId()).isEqualTo(BOOK_KYOBO);
        assertThat(detail.libraryCount()).isEqualTo(2);
        assertThat(detail.availableLibraryCount()).isEqualTo(1);
        assertThat(detail.bookUrl()).isEqualTo("https://book.example/k-1");
        assertThat(detail.libraries())
            .extracting(
                PlatformBookDetailResponse.LibraryItem::libraryId,
                PlatformBookDetailResponse.LibraryItem::availabilityStatus,
                PlatformBookDetailResponse.LibraryItem::bookUrl
            )
            .containsExactly(
                tuple(LIB_GARAM, AvailabilityStatus.AVAILABLE, "https://library.example/301/201"),
                tuple(LIB_NARAE, AvailabilityStatus.UNKNOWN, "https://library.example/301/202")
            );
        assertThat(detail.relatedBooks())
            .extracting(
                PlatformBookDetailResponse.RelatedBook::platformId,
                PlatformBookDetailResponse.RelatedBook::platformType,
                PlatformBookDetailResponse.RelatedBook::matchType
            )
            .containsExactly(tuple(MILLIE, "SUBSCRIPTION", "ISBN"), tuple(YES24, "LIBRARY", "TITLE_AUTHOR"));
        assertThat(favoriteFlag(detail)).isTrue();
        assertThat(detail.libraries()).extracting(PlatformBookDetailResponse.LibraryItem::libraryId).hasSize(2);
        assertThat(libraryFavoriteFlags(detail)).containsExactly(true, false);
    }

    @Test
    @DisplayName("로그인하지 않으면 도서 즐겨찾기는 false, 도서관 즐겨찾기는 null")
    void getPlatformBookWithoutUserHasNoFavorites() {
        // when
        PlatformBookDetailResponse detail = bookSearchService.getPlatformBook(KYOBO, "k-1", null);

        // then
        assertThat(favoriteFlag(detail)).isFalse();
        assertThat(libraryFavoriteFlags(detail)).containsExactly(null, null);
    }

    @Test
    @DisplayName("구독 플랫폼 도서 상세는 구독 도서의 URL과 표지를 쓰고 도서관 목록은 비어 있음")
    void getPlatformBookForSubscriptionUsesSubscriptionAsset() {
        // when
        PlatformBookDetailResponse detail = bookSearchService.getPlatformBook(MILLIE, "m-1", null);

        // then
        assertThat(detail.platformType()).isEqualTo("SUBSCRIPTION");
        assertThat(detail.bookUrl()).isEqualTo("https://millie.example/m-1");
        assertThat(detail.coverImgUrl()).isEqualTo("https://millie.example/m-1.jpg");
        assertThat(detail.libraries()).isEmpty();
        assertThat(detail.libraryCount()).isZero();
    }

    @Test
    @DisplayName("같은 플랫폼 도서 ID의 전자책과 오디오북이 함께 있으면 전자책 상세 반환")
    void getPlatformBookPrefersEbookWhenIdIsShared() {
        // given
        jdbcTemplate.update("""
            insert into book_origin (book_origin_id, platform_id, platform_book_id, content_type, title_raw,
                title_norm, is_active)
            values (300, ?, 'k-1', 'AUDIOBOOK', '자바의 정석 오디오북', '자바의정석오디오북', true)
            """, KYOBO);

        // when
        PlatformBookDetailResponse detail = bookSearchService.getPlatformBook(KYOBO, "k-1", null);

        // then
        assertThat(detail.contentType()).isEqualTo(ContentType.EBOOK);
        assertThat(detail.title()).isEqualTo("자바의 정석");
    }

    @Test
    @DisplayName("플랫폼 도서 상세 오류: 없는 플랫폼, 없는 도서, 도서관과 구독을 모두 지원하지 않는 플랫폼")
    void getPlatformBookErrors() {
        assertBusinessError(() -> bookSearchService.getPlatformBook(9999L, "k-1", null),
            "platform_not_found", HttpStatus.NOT_FOUND, "존재하지 않는 플랫폼입니다.");
        assertBusinessError(() -> bookSearchService.getPlatformBook(KYOBO, "none", null),
            "platform_book_not_found", HttpStatus.NOT_FOUND, "존재하지 않는 플랫폼 도서입니다.");
        assertBusinessError(() -> bookSearchService.getPlatformBook(KYOBO, "k-2", null),
            "platform_book_not_found", HttpStatus.NOT_FOUND, "존재하지 않는 플랫폼 도서입니다.");
        assertBusinessError(() -> bookSearchService.getPlatformBook(ETC, "e-1", null),
            "platform_book_not_found", HttpStatus.NOT_FOUND, "존재하지 않는 플랫폼 도서입니다.");
    }

    @Test
    @DisplayName("플랫폼별 검색은 지원 플랫폼마다 이름순 그룹으로 최대 3건씩 반환하고 결과 없는 플랫폼은 제외")
    void searchPlatformBooksGroupsByPlatform() {
        // when
        PlatformBookSearchResponse response = bookSearchService.searchPlatformBooks(new PlatformBookSearchRequest("자바", "TITLE", null, "ALL", null, null, 0, 20), USER_ID);

        // then
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.groups())
            .extracting(
                PlatformBookSearchResponse.Group::platformId,
                PlatformBookSearchResponse.Group::platformType,
                PlatformBookSearchResponse.Group::totalElements
            )
            .containsExactly(tuple(KYOBO, "LIBRARY", 1L), tuple(MILLIE, "SUBSCRIPTION", 1L), tuple(YES24, "LIBRARY", 1L));
        PlatformBookSearchResponse.Item kyoboItem = response.groups().get(0).content().get(0);
        assertThat(kyoboItem.libraryCount()).isEqualTo(2);
        assertThat(kyoboItem.availableLibraryCount()).isEqualTo(1);
        assertThat(favoriteFlag(kyoboItem)).isTrue();
        PlatformBookSearchResponse.Item millieItem = response.groups().get(1).content().get(0);
        assertThat(millieItem.bookUrl()).isEqualTo("https://millie.example/m-1");
        assertThat(millieItem.libraryCount()).isZero();
    }

    @Test
    @DisplayName("플랫폼별 검색은 플랫폼 종류와 선택한 플랫폼으로 그룹을 좁힘")
    void searchPlatformBooksFiltersByTypeAndSelectedPlatforms() {
        // when
        PlatformBookSearchResponse libraryOnly = bookSearchService.searchPlatformBooks(new PlatformBookSearchRequest("자바", "TITLE", null, "library", null, null, 0, 20), null);
        PlatformBookSearchResponse selected = bookSearchService.searchPlatformBooks(new PlatformBookSearchRequest("자바", "TITLE", null, "ALL", List.of(YES24, MILLIE), null, 0, 20), null);

        // then
        assertThat(libraryOnly.groups()).extracting(PlatformBookSearchResponse.Group::platformId)
            .containsExactly(KYOBO, YES24);
        assertThat(selected.groups()).extracting(PlatformBookSearchResponse.Group::platformId)
            .containsExactly(MILLIE, YES24);
    }

    @Test
    @DisplayName("단일 플랫폼 검색은 결과가 없어도 그룹을 반환하고 요청한 페이지 크기로 조회")
    void searchPlatformBooksForSinglePlatformUsesPaging() {
        // when
        PlatformBookSearchResponse response = bookSearchService.searchPlatformBooks(new PlatformBookSearchRequest("없는 제목", "TITLE", null, "ALL", null, KYOBO, 0, 1), null);

        // then
        assertThat(response.groups()).hasSize(1);
        assertThat(response.groups().get(0).content()).isEmpty();
        assertThat(response.groups().get(0).last()).isTrue();
    }

    @Test
    @DisplayName("플랫폼별 검색 요청 오류")
    void searchPlatformBooksErrors() {
        String invalid = "invalid_platform_book_request";
        assertBusinessError(() -> search(" ", "TITLE", "ALL", null, null, 0, 20),
            invalid, HttpStatus.BAD_REQUEST, "플랫폼 도서 요청이 올바르지 않습니다.");
        assertBusinessError(() -> search("자바", "TITLE", "ALL", null, null, -1, 20),
            invalid, HttpStatus.BAD_REQUEST, "플랫폼 도서 요청이 올바르지 않습니다.");
        assertBusinessError(() -> search("자바", "TITLE", "ALL", null, null, 0, 101),
            invalid, HttpStatus.BAD_REQUEST, "플랫폼 도서 요청이 올바르지 않습니다.");
        assertBusinessError(() -> search("자바", "TITLE", "EBOOK", null, null, 0, 20),
            invalid, HttpStatus.BAD_REQUEST, "플랫폼 도서 요청이 올바르지 않습니다.");
        assertBusinessError(() -> search("자바", "ALL", "ALL", null, null, 0, 20),
            invalid, HttpStatus.BAD_REQUEST, "플랫폼 도서 요청이 올바르지 않습니다.");
        assertBusinessError(() -> search("자바", "TITLE", "LIBRARY", null, MILLIE, 0, 20),
            invalid, HttpStatus.BAD_REQUEST, "플랫폼 도서 요청이 올바르지 않습니다.");
        assertBusinessError(() -> search("자바", "TITLE", "ALL", List.of(9999L), null, 0, 20),
            "platform_not_found", HttpStatus.NOT_FOUND, "존재하지 않는 플랫폼입니다.");
        assertBusinessError(() -> search("자바", "TITLE", "ALL", null, 9999L, 0, 20),
            "platform_not_found", HttpStatus.NOT_FOUND, "존재하지 않는 플랫폼입니다.");
    }

    @Test
    @DisplayName("통합 검색은 활성 도서를 제목순으로 반환하고 카테고리, 도서관 플랫폼, 구독 플랫폼 요약을 붙임")
    void searchBooksReturnsSummaries() {
        // when
        BookSearchPageResponse response = bookSearchService.searchBooks(new BookSearchRequest("자바", "TITLE", null, null, null, null), PageRequest.of(0, 20));

        // then
        assertThat(response.books().content()).extracting(BookSearchResponse::bookOriginId)
            .containsExactly(BOOK_ETC, BOOK_KYOBO, BOOK_MILLIE, BOOK_YES);
        BookSearchResponse kyobo = response.books().content().get(1);
        assertThat(kyobo.primaryCategoryName()).isEqualTo("소설");
        assertThat(kyobo.libraryPlatforms()).containsExactly(
            new BookPlatformSummaryResponse(KYOBO, "KYOBO", "교보문고", 1)
        );
        assertThat(kyobo.libraryCount()).isEqualTo(1);
        BookSearchResponse millie = response.books().content().get(2);
        assertThat(millie.platformType()).isEqualTo("SUBSCRIPTION");
        assertThat(millie.subscriptionPlatforms()).containsExactly(
            new BookPlatformSummaryResponse(MILLIE, "MILLIE", "밀리의서재", 1)
        );
        assertThat(response.books().content().get(0).platformType()).isNull();
    }

    @Test
    @DisplayName("통합 검색 필터: 카테고리, 도서관 플랫폼, 구독 플랫폼, 도서관")
    void searchBooksAppliesFilters() {
        assertThat(searchBookIds(List.of(CATEGORY_NOVEL), null, null, null)).containsExactly(BOOK_KYOBO);
        assertThat(searchBookIds(null, null, List.of(YES24), null)).containsExactly(BOOK_YES);
        assertThat(searchBookIds(null, null, null, List.of(MILLIE))).containsExactly(BOOK_MILLIE);
        assertThat(searchBookIds(null, List.of(LIB_GARAM), null, null)).containsExactly(BOOK_KYOBO);
        assertThat(searchBookIds(null, List.of(LIB_NARAE), null, null)).isEmpty();
        assertThat(searchBookIds(null, null, List.of(YES24), List.of(MILLIE))).containsExactly(BOOK_MILLIE, BOOK_YES);
    }

    @Test
    @DisplayName("도서 상세는 이용 가능한 노출 도서관 보유 현황과 카테고리를 반환, 비활성 도서는 book_not_found")
    void getBookReturnsHoldingsAndRejectsInactiveBook() {
        // when
        BookSearchResponse book = bookSearchService.getBook(BOOK_KYOBO);

        // then
        assertThat(book.primaryCategoryId()).isEqualTo(CATEGORY_NOVEL);
        assertThat(book.libraryPlatformHoldings()).hasSize(1);
        assertThat(book.libraryPlatformHoldings().get(0).libraryCount()).isEqualTo(1);
        assertThat(book.libraryPlatformHoldings().get(0).libraries())
            .extracting("libraryId").containsExactly(LIB_GARAM);
        assertThat(book.subscriptionPlatformLinks()).isEmpty();
        assertThat(bookSearchService.getBook(BOOK_MILLIE).subscriptionPlatformLinks())
            .extracting("bookUrl").containsExactly("https://millie.example/m-1");
        assertBusinessError(() -> bookSearchService.getBook(BOOK_INACTIVE),
            "book_not_found", HttpStatus.NOT_FOUND, "도서를 찾을 수 없습니다.");
    }

    private PlatformBookSearchResponse search(
        String keyword, String searchType, String platformType, List<Long> platformIds, Long platformId,
        int page, int size
    ) {
        return bookSearchService.searchPlatformBooks(new PlatformBookSearchRequest(keyword, searchType, null, platformType, platformIds, platformId, page, size), null);
    }

    private List<Long> searchBookIds(
        List<Long> categoryIds, List<Long> libraryIds, List<Long> libraryPlatformIds, List<Long> subscriptionPlatformIds
    ) {
        return bookSearchService.searchBooks(new BookSearchRequest("자바", "TITLE", categoryIds, libraryIds, libraryPlatformIds, subscriptionPlatformIds), PageRequest.of(0, 20))
            .books().content().stream().map(BookSearchResponse::bookOriginId).toList();
    }

    /** 리팩토링 중 내부 필드명이 favoriteYn에서 favorite로 바뀌어도 같은 검증을 하기 위한 리플렉션 조회 */
    private Boolean favoriteFlag(Object response) {
        return (Boolean) readComponent(response, "favoriteYn", "favorite");
    }

    private List<Boolean> libraryFavoriteFlags(PlatformBookDetailResponse detail) {
        return detail.libraries().stream().map(this::favoriteFlag).toList();
    }

    private Object readComponent(Object record, String... names) {
        for (var component : record.getClass().getRecordComponents()) {
            for (String name : names) {
                if (component.getName().equals(name)) {
                    try {
                        return component.getAccessor().invoke(record);
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException(exception);
                    }
                }
            }
        }
        throw new IllegalArgumentException("record component not found");
    }

    private void assertBusinessError(ThrowingCallable call, String code, HttpStatus status, String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode().code()).isEqualTo(code);
            assertThat(exception.getErrorCode().httpStatus()).isEqualTo(status);
            assertThat(exception.getErrorCode().message()).isEqualTo(message);
        });
    }

    private void insertPlatform(long id, String code, String name, boolean library, boolean subscription) {
        jdbcTemplate.update("""
            insert into platform (platform_id, platform_code, platform_name, supports_library, supports_subscription)
            values (?, ?, ?, ?, ?)
            """, id, code, name, library, subscription);
    }

    private void insertLibrary(long id, long platformId, String name, boolean visible) {
        jdbcTemplate.update("""
            insert into library (library_id, platform_id, library_code, library_name, site_url, is_visible)
            values (?, ?, ?, ?, ?, ?)
            """, id, platformId, "lib-" + id, name, "https://library.example/" + id, visible);
    }

    private void insertBook(long id, long platformId, String platformBookId, String title, String isbn, boolean active) {
        jdbcTemplate.update("""
            insert into book_origin (book_origin_id, platform_id, platform_book_id, content_type, title_raw,
                title_norm, author_raw, author_norm, isbn13_raw, book_url, cover_img_url, is_active)
            values (?, ?, ?, 'EBOOK', ?, ?, '남궁성', '남궁성', ?, ?, ?, ?)
            """, id, platformId, platformBookId, title, title.replace(" ", ""), isbn,
            "https://book.example/" + platformBookId, "https://book.example/" + platformBookId + ".jpg", active);
    }

    private void insertLibraryBook(long bookId, long libraryId, String status) {
        jdbcTemplate.update("""
            insert into library_book (book_origin_id, library_id, availability_status, book_url)
            values (?, ?, ?, ?)
            """, bookId, libraryId, status, "https://library.example/" + bookId + "/" + libraryId);
    }
}
