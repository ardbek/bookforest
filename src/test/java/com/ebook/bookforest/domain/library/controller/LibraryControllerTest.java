package com.ebook.bookforest.domain.library.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebook.bookforest.domain.library.dto.LibraryPageResponse;
import com.ebook.bookforest.domain.library.dto.LibraryResponse;
import com.ebook.bookforest.domain.library.service.LibraryService;
import com.ebook.bookforest.domain.searchkeyword.config.SearchKeywordWebConfig;
import com.ebook.bookforest.global.security.jwt.JwtAuthenticationFilter;
import com.ebook.bookforest.global.security.jwt.JwtExceptionHandlerFilter;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 도서관 목록 API 계약 검증
 * 보안 필터와 검색어 집계 설정은 제외하고 URL, 파라미터 바인딩, 응답 JSON 형식만 확인
 */
@WebMvcTest(
    controllers = LibraryController.class,
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
class LibraryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LibraryService libraryService;

    @Test
    @DisplayName("도서관 목록 응답은 기존 JSON 구조와 필드명 유지")
    void searchLibrariesKeepsResponseContract() throws Exception {
        // given
        LibraryResponse library = new LibraryResponse(5L, "서울도서관", "KYOBO", "https://library.example", false);
        given(libraryService.searchLibraries(eq(1L), eq("서울"), any(Pageable.class)))
            .willReturn(LibraryPageResponse.from(new PageImpl<>(List.of(library), PageRequest.of(0, 10), 1)));

        // when, then
        mockMvc.perform(get("/api/library")
                .param("platformId", "1")
                .param("searchKeyword", "서울"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", aMapWithSize(1)))
            .andExpect(jsonPath("$.libraries", aMapWithSize(3)))
            .andExpect(jsonPath("$.libraries.totalElements").value(1))
            .andExpect(jsonPath("$.libraries.last").value(true))
            .andExpect(jsonPath("$.libraries.content[0]", aMapWithSize(5)))
            .andExpect(jsonPath("$.libraries.content[0].libraryId").value(5))
            .andExpect(jsonPath("$.libraries.content[0].libraryName").value("서울도서관"))
            .andExpect(jsonPath("$.libraries.content[0].platform").value("KYOBO"))
            .andExpect(jsonPath("$.libraries.content[0].siteUrl").value("https://library.example"))
            .andExpect(jsonPath("$.libraries.content[0].favoriteYn").value(false));
    }

    @Test
    @DisplayName("페이지 파라미터가 없으면 첫 페이지와 크기 10으로 조회")
    void searchLibrariesUsesDefaultPageSize() throws Exception {
        // given
        given(libraryService.searchLibraries(eq(1L), eq(null), any(Pageable.class)))
            .willReturn(LibraryPageResponse.from(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0)));

        // when
        mockMvc.perform(get("/api/library").param("platformId", "1"))
            .andExpect(status().isOk());

        // then
        then(libraryService).should().searchLibraries(eq(1L), eq(null), eq(PageRequest.of(0, 10)));
    }

    @Test
    @DisplayName("platformId가 숫자가 아니면 invalid_request 오류 응답")
    void searchLibrariesRejectsInvalidPlatformId() throws Exception {
        // when, then
        mockMvc.perform(get("/api/library").param("platformId", "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("invalid_request"));
    }
}
