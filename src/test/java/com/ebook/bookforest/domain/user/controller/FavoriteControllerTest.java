package com.ebook.bookforest.domain.user.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebook.bookforest.domain.searchkeyword.config.SearchKeywordWebConfig;
import com.ebook.bookforest.domain.user.dto.FavoriteBookPageResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteBookResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteLibraryPageResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteLibraryResponse;
import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.domain.user.service.FavoriteService;
import com.ebook.bookforest.global.security.jwt.JwtAuthenticationFilter;
import com.ebook.bookforest.global.security.jwt.JwtExceptionHandlerFilter;
import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 즐겨찾기 API 계약 검증
 * 보안 필터와 검색어 집계 설정은 제외하고 로그인 사용자를 직접 지정해 URL, 응답 JSON, 사용자 전달을 확인
 */
@WebMvcTest(
    controllers = FavoriteController.class,
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
class FavoriteControllerTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FavoriteService favoriteService;

    @AfterEach
    void tearDown() {
        TestSecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("즐겨찾기 도서 목록 응답은 기존 JSON 구조와 필드명 유지, 기본 페이지 크기 8")
    void getFavoriteBooksKeepsResponseContract() throws Exception {
        // given
        FavoriteBookResponse book = new FavoriteBookResponse(
            10L, 2L, "YES24", "예스24", "불편한 편의점", "김호연", "나무옆의자",
            LocalDate.of(2021, 4, 20), "https://cover.example/10"
        );
        given(favoriteService.getFavoriteBooks(eq(USER_ID), any(Pageable.class)))
            .willReturn(FavoriteBookPageResponse.from(new PageImpl<>(List.of(book), PageRequest.of(0, 8), 1)));

        // when, then
        mockMvc.perform(get("/api/users/me/favorite-books").with(loginUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", aMapWithSize(1)))
            .andExpect(jsonPath("$.favoriteBooks", aMapWithSize(3)))
            .andExpect(jsonPath("$.favoriteBooks.totalElements").value(1))
            .andExpect(jsonPath("$.favoriteBooks.last").value(true))
            .andExpect(jsonPath("$.favoriteBooks.content[0]", aMapWithSize(9)))
            .andExpect(jsonPath("$.favoriteBooks.content[0].bookOriginId").value(10))
            .andExpect(jsonPath("$.favoriteBooks.content[0].platformId").value(2))
            .andExpect(jsonPath("$.favoriteBooks.content[0].platformCode").value("YES24"))
            .andExpect(jsonPath("$.favoriteBooks.content[0].platformName").value("예스24"))
            .andExpect(jsonPath("$.favoriteBooks.content[0].title").value("불편한 편의점"))
            .andExpect(jsonPath("$.favoriteBooks.content[0].author").value("김호연"))
            .andExpect(jsonPath("$.favoriteBooks.content[0].publisher").value("나무옆의자"))
            .andExpect(jsonPath("$.favoriteBooks.content[0].publishedDate").value("2021-04-20"))
            .andExpect(jsonPath("$.favoriteBooks.content[0].coverImgUrl").value("https://cover.example/10"));

        then(favoriteService).should().getFavoriteBooks(USER_ID, PageRequest.of(0, 8));
    }

    @Test
    @DisplayName("즐겨찾기 도서 ID 목록은 숫자 배열로 응답")
    void getFavoriteBookOriginIdsReturnsArray() throws Exception {
        // given
        given(favoriteService.getFavoriteBookOriginIds(USER_ID)).willReturn(List.of(10L, 11L));

        // when, then
        mockMvc.perform(get("/api/users/me/favorite-books/ids").with(loginUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", contains(10, 11)));
    }

    @Test
    @DisplayName("즐겨찾기 도서 추가와 해제는 로그인 사용자 기준으로 처리하고 본문 없이 204 응답")
    void addAndRemoveFavoriteBookReturnNoContent() throws Exception {
        // when, then
        mockMvc.perform(post("/api/users/me/favorite-books/10").with(loginUser()))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
        mockMvc.perform(delete("/api/users/me/favorite-books/10").with(loginUser()))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        then(favoriteService).should().addFavoriteBook(USER_ID, 10L);
        then(favoriteService).should().removeFavoriteBook(USER_ID, 10L);
    }

    @Test
    @DisplayName("즐겨찾기 도서관 목록 응답은 기존 JSON 구조와 필드명 유지, 기본 페이지 크기 8")
    void getFavoriteLibrariesKeepsResponseContract() throws Exception {
        // given
        FavoriteLibraryResponse library = new FavoriteLibraryResponse(
            20L, "서울도서관", "YES24", "예스24", "https://library.example/20"
        );
        given(favoriteService.getFavoriteLibraries(eq(USER_ID), any(Pageable.class)))
            .willReturn(FavoriteLibraryPageResponse.from(new PageImpl<>(List.of(library), PageRequest.of(0, 8), 1)));

        // when, then
        mockMvc.perform(get("/api/users/me/favorite-libraries").with(loginUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", aMapWithSize(1)))
            .andExpect(jsonPath("$.favoriteLibraries", aMapWithSize(3)))
            .andExpect(jsonPath("$.favoriteLibraries.totalElements").value(1))
            .andExpect(jsonPath("$.favoriteLibraries.last").value(true))
            .andExpect(jsonPath("$.favoriteLibraries.content[0]", aMapWithSize(5)))
            .andExpect(jsonPath("$.favoriteLibraries.content[0].libraryId").value(20))
            .andExpect(jsonPath("$.favoriteLibraries.content[0].libraryName").value("서울도서관"))
            .andExpect(jsonPath("$.favoriteLibraries.content[0].platformCode").value("YES24"))
            .andExpect(jsonPath("$.favoriteLibraries.content[0].platformName").value("예스24"))
            .andExpect(jsonPath("$.favoriteLibraries.content[0].siteUrl").value("https://library.example/20"));

        then(favoriteService).should().getFavoriteLibraries(USER_ID, PageRequest.of(0, 8));
    }

    @Test
    @DisplayName("즐겨찾기 도서관 ID 목록은 숫자 배열로 응답")
    void getFavoriteLibraryIdsReturnsArray() throws Exception {
        // given
        given(favoriteService.getFavoriteLibraryIds(USER_ID)).willReturn(List.of(20L));

        // when, then
        mockMvc.perform(get("/api/users/me/favorite-libraries/ids").with(loginUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", contains(20)));
    }

    @Test
    @DisplayName("즐겨찾기 도서관 추가와 해제는 로그인 사용자 기준으로 처리하고 본문 없이 204 응답")
    void addAndRemoveFavoriteLibraryReturnNoContent() throws Exception {
        // when, then
        mockMvc.perform(post("/api/users/me/favorite-libraries/20").with(loginUser()))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
        mockMvc.perform(delete("/api/users/me/favorite-libraries/20").with(loginUser()))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        then(favoriteService).should().addFavoriteLibrary(USER_ID, 20L);
        then(favoriteService).should().removeFavoriteLibrary(USER_ID, 20L);
    }

    /** 보안 필터 없이 @AuthenticationPrincipal에 로그인 사용자를 전달하기 위한 보안 컨텍스트 지정 */
    private RequestPostProcessor loginUser() {
        JwtPrincipal principal = new JwtPrincipal(USER_ID, UserRole.ROLE_USER);
        return request -> {
            TestSecurityContextHolder.setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
            );
            return request;
        };
    }
}
