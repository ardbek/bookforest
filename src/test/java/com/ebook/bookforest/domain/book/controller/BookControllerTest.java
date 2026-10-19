package com.ebook.bookforest.domain.book.controller;

import com.ebook.bookforest.domain.book.dto.PlatformBookSearchRequest;
import com.ebook.bookforest.domain.book.dto.BookSearchRequest;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebook.bookforest.domain.book.dto.BookLibraryHoldingResponse;
import com.ebook.bookforest.domain.book.dto.BookLibraryPlatformHoldingResponse;
import com.ebook.bookforest.domain.book.dto.BookPlatformSummaryResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchPageResponse;
import com.ebook.bookforest.domain.book.dto.BookSearchResponse;
import com.ebook.bookforest.domain.book.dto.BookSubscriptionPlatformResponse;
import com.ebook.bookforest.domain.book.dto.PlatformBookDetailResponse;
import com.ebook.bookforest.domain.book.dto.PlatformBookSearchResponse;
import com.ebook.bookforest.domain.book.enums.AvailabilityStatus;
import com.ebook.bookforest.domain.book.enums.ContentType;
import com.ebook.bookforest.domain.book.service.BookSearchService;
import com.ebook.bookforest.domain.searchkeyword.service.SearchKeywordService;
import com.ebook.bookforest.domain.searchkeyword.config.SearchKeywordWebConfig;
import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.global.security.jwt.JwtAuthenticationFilter;
import com.ebook.bookforest.global.security.jwt.JwtExceptionHandlerFilter;
import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
import java.time.LocalDate;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 도서 검색, 상세 API 계약 검증
 * 보안 필터와 검색어 집계 설정은 제외하고 URL, 파라미터 바인딩, 응답 JSON 필드명, 검색어 기록 호출을 확인
 */
@WebMvcTest(
    controllers = BookController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            JwtAuthenticationFilter.class,
            JwtExceptionHandlerFilter.class,
            SearchKeywordWebConfig.class
        }
    )
)
@AutoConfigureMockMvc(addFilters = false)
class BookControllerTest {

    private static final String[] BOOK_FIELDS = {
        "bookOriginId", "platformId", "platformCode", "platformName", "platformType", "contentType", "title",
        "author", "publisher", "publishedDate", "isbn13", "isbn10", "coverImgUrl", "description",
        "primaryCategoryId", "primaryCategoryName", "primaryCategoryPath", "libraryCount", "libraryPlatformCount",
        "subscriptionPlatformCount", "libraryPlatforms", "subscriptionPlatforms", "subscriptionPlatformLinks",
        "libraryPlatformHoldings", "favoriteYn"
    };

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookSearchService bookSearchService;

    @MockitoBean
    private SearchKeywordService searchKeywordService;

    @AfterEach
    void tearDown() {
        TestSecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("통합 검색 응답은 기존 JSON 구조 유지, 기본 페이지 크기 20, 검색어 기록")
    void searchBooksKeepsContract() throws Exception {
        // given
        BookSearchRequest expectedRequest = new BookSearchRequest("자바", "TITLE", List.of(1L, 2L), null, null, List.of(3L));
        given(bookSearchService.searchBooks(eq(expectedRequest), any(Pageable.class))).willReturn(BookSearchPageResponse.from(new PageImpl<>(List.of(book()), PageRequest.of(0, 20), 1)));

        // when, then
        mockMvc.perform(get("/api/book/search")
                .param("keyword", "자바")
                .param("searchType", "TITLE")
                .param("categoryIds", "1", "2")
                .param("subscriptionPlatformIds", "3")
                .header("X-Search-Event-Id", "event-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", aMapWithSize(1)))
            .andExpect(jsonPath("$.books", keys("content", "totalElements", "last")))
            .andExpect(jsonPath("$.books.content[0]", keys(BOOK_FIELDS)))
            .andExpect(jsonPath("$.books.content[0].favoriteYn").value(false))
            .andExpect(jsonPath("$.books.content[0].libraryPlatforms[0]",
                keys("platformId", "platformCode", "platformName", "itemCount")));

        then(bookSearchService).should().searchBooks(expectedRequest, PageRequest.of(0, 20));
        then(searchKeywordService).should().record(null, "event-1", "자바", 0, 1L);
    }

    @Test
    @DisplayName("플랫폼별 검색 응답은 기존 JSON 구조 유지, 로그인 사용자 전달, 검색어 기록")
    void searchPlatformBooksKeepsContract() throws Exception {
        // given
        login();
        PlatformBookSearchResponse.Item item = new PlatformBookSearchResponse.Item(
            "k-1", 10L, ContentType.EBOOK, "자바의 정석", "남궁성", "도우출판", LocalDate.of(2016, 1, 27),
            "9780000000001", "https://cover", "https://book", 2, 1, true
        );
        PlatformBookSearchRequest expectedRequest =
            new PlatformBookSearchRequest("자바", null, null, "ALL", List.of(5L), null, 1, 3);
        given(bookSearchService.searchPlatformBooks(expectedRequest, 7L))
            .willReturn(new PlatformBookSearchResponse(1, List.of(new PlatformBookSearchResponse.Group(
                5L, "KYOBO", "교보문고", "LIBRARY", 1, true, List.of(item)
            ))));

        // when, then
        mockMvc.perform(get("/api/book/search/platforms")
                .param("keyword", "자바")
                .param("platformType", "ALL")
                .param("platformIds", "5")
                .param("page", "1")
                .param("size", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", keys("totalElements", "groups")))
            .andExpect(jsonPath("$.groups[0]", keys(
                "platformId", "platformCode", "platformName", "platformType", "totalElements", "last", "content"
            )))
            .andExpect(jsonPath("$.groups[0].content[0]", keys(
                "sourceBookId", "canonicalBookId", "contentType", "title", "author", "publisher", "publishedDate",
                "isbn13", "coverImgUrl", "bookUrl", "libraryCount", "availableLibraryCount", "favoriteYn"
            )))
            .andExpect(jsonPath("$.groups[0].content[0].sourceBookId").value("k-1"))
            .andExpect(jsonPath("$.groups[0].content[0].favoriteYn").value(true));

        then(searchKeywordService).should().record(null, null, "자바", 1, 1L);
    }

    @Test
    @DisplayName("플랫폼 도서 상세 응답은 기존 JSON 구조 유지, 비로그인이면 사용자 없이 조회")
    void getPlatformBookKeepsContract() throws Exception {
        // given
        PlatformBookDetailResponse detail = new PlatformBookDetailResponse(
            5L, "KYOBO", "교보문고", "LIBRARY", "k-1", 10L, ContentType.EBOOK, "자바의 정석", "남궁성", "도우출판",
            LocalDate.of(2016, 1, 27), "9780000000001", "https://cover", "https://book", 1, 1, false, "설명",
            List.of(new PlatformBookDetailResponse.LibraryItem(
                20L, "lib-20", "가람도서관", "https://lib", "https://lib/book", "https://lib/cover",
                AvailabilityStatus.AVAILABLE, true, null
            )),
            List.of(new PlatformBookDetailResponse.RelatedBook(6L, "m-1", "밀리의서재", "SUBSCRIPTION", "자바의 정석",
                "남궁성", "ISBN"))
        );
        given(bookSearchService.getPlatformBook(5L, "k-1", null)).willReturn(detail);

        // when, then
        mockMvc.perform(get("/api/book/platforms/5/books/k-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", keys(
                "platformId", "platformCode", "platformName", "platformType", "sourceBookId", "canonicalBookId",
                "contentType", "title", "author", "publisher", "publishedDate", "isbn13", "coverImgUrl", "bookUrl",
                "libraryCount", "availableLibraryCount", "favoriteYn", "description", "libraries", "relatedBooks"
            )))
            .andExpect(jsonPath("$.libraries[0]", keys(
                "libraryId", "libraryCode", "libraryName", "siteUrl", "bookUrl", "coverImgUrl", "availabilityStatus",
                "availableYn", "favoriteYn"
            )))
            .andExpect(jsonPath("$.libraries[0].availableYn").value(true))
            .andExpect(jsonPath("$.libraries[0].favoriteYn").value(nullValue()))
            .andExpect(jsonPath("$.relatedBooks[0]", keys(
                "platformId", "sourceBookId", "platformName", "platformType", "title", "author", "matchType"
            )));
    }

    @Test
    @DisplayName("도서 상세 응답은 기존 JSON 구조 유지")
    void getBookKeepsContract() throws Exception {
        // given
        given(bookSearchService.getBook(10L)).willReturn(book());

        // when, then
        mockMvc.perform(get("/api/book/10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", keys(BOOK_FIELDS)))
            .andExpect(jsonPath("$.subscriptionPlatformLinks[0]",
                keys("platformId", "platformCode", "platformName", "bookUrl")))
            .andExpect(jsonPath("$.libraryPlatformHoldings[0]",
                keys("platformId", "platformCode", "platformName", "libraryCount", "libraries")))
            .andExpect(jsonPath("$.libraryPlatformHoldings[0].libraries[0]", keys(
                "libraryId", "libraryCode", "libraryName", "siteUrl", "bookUrl", "coverImgUrl", "availabilityStatus",
                "availableYn"
            )))
            .andExpect(jsonPath("$.libraryPlatformHoldings[0].libraries[0].availableYn").value(true));
    }

    private BookSearchResponse book() {
        return new BookSearchResponse(
            10L, 5L, "KYOBO", "교보문고", "LIBRARY", ContentType.EBOOK, "자바의 정석", "남궁성", "도우출판",
            LocalDate.of(2016, 1, 27), "9780000000001", null, "https://cover", "설명", 1L, "소설", "소설", 1, 1, 0,
            List.of(new BookPlatformSummaryResponse(5L, "KYOBO", "교보문고", 1)),
            List.of(),
            List.of(new BookSubscriptionPlatformResponse(6L, "MILLIE", "밀리의서재", "https://millie")),
            List.of(BookLibraryPlatformHoldingResponse.of(5L, "KYOBO", "교보문고", List.of(
                new BookLibraryHoldingResponse(20L, "lib-20", "가람도서관", "https://lib", "https://lib/book",
                    "https://lib/cover", AvailabilityStatus.AVAILABLE, true)
            ))),
            false
        );
    }

    private void login() {
        TestSecurityContextHolder.setAuthentication(new UsernamePasswordAuthenticationToken(
            new JwtPrincipal(7L, UserRole.ROLE_USER), null, List.of()
        ));
    }

    @SuppressWarnings("unchecked")
    private static Matcher<Object> keys(String... names) {
        Matcher<? super java.util.Map<String, ?>>[] matchers = new Matcher[names.length + 1];
        matchers[0] = aMapWithSize(names.length);
        for (int i = 0; i < names.length; i++) {
            matchers[i + 1] = hasKey(names[i]);
        }
        return (Matcher<Object>) (Matcher<?>) allOf(matchers);
    }
}
