package com.ebook.bookforest.domain.searchkeyword.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebook.bookforest.domain.searchkeyword.config.SearchKeywordWebConfig;
import com.ebook.bookforest.domain.searchkeyword.dto.PopularSearchKeywordResponse;
import com.ebook.bookforest.domain.searchkeyword.service.SearchKeywordService;
import com.ebook.bookforest.global.security.jwt.JwtAuthenticationFilter;
import com.ebook.bookforest.global.security.jwt.JwtExceptionHandlerFilter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 인기 검색어 API 계약 검증
 */
@WebMvcTest(
    controllers = PopularSearchKeywordController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtAuthenticationFilter.class, JwtExceptionHandlerFilter.class, SearchKeywordWebConfig.class}
    )
)
@AutoConfigureMockMvc(addFilters = false)
class PopularSearchKeywordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchKeywordService searchKeywordService;

    @Test
    @DisplayName("인기 검색어 응답은 기존 JSON 구조 유지, 기본 개수 10, 캐시 금지 헤더")
    void popularKeywordsKeepContract() throws Exception {
        // given
        given(searchKeywordService.getPopularKeywords(10)).willReturn(new PopularSearchKeywordResponse(
            OffsetDateTime.of(2026, 10, 15, 3, 0, 0, 0, ZoneOffset.UTC),
            "PT24H",
            List.of(new PopularSearchKeywordResponse.Item(1, "한강"))
        ));

        // when, then
        mockMvc.perform(get("/api/search-keywords/popular"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$", aMapWithSize(3)))
            .andExpect(jsonPath("$.window").value("PT24H"))
            .andExpect(jsonPath("$.items[0]", aMapWithSize(2)))
            .andExpect(jsonPath("$.items[0].rank").value(1))
            .andExpect(jsonPath("$.items[0].keyword").value("한강"));
    }

    @Test
    @DisplayName("요청 개수가 1~20 범위를 벗어나면 본문 없는 400, 조회 미호출")
    void popularKeywordsRejectsOutOfRangeLimit() throws Exception {
        // when, then
        mockMvc.perform(get("/api/search-keywords/popular").param("limit", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(""));
        mockMvc.perform(get("/api/search-keywords/popular").param("limit", "21"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(""));

        then(searchKeywordService).should(never()).getPopularKeywords(anyInt());
    }
}
