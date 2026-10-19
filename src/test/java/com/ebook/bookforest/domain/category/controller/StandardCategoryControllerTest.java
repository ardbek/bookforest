package com.ebook.bookforest.domain.category.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebook.bookforest.domain.category.dto.StandardCategoryResponse;
import com.ebook.bookforest.domain.category.service.StandardCategoryService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 표준 카테고리 목록 API 계약 검증
 * 보안 필터와 검색어 집계 설정은 제외하고 URL과 응답 JSON 형식만 확인
 */
@WebMvcTest(
    controllers = StandardCategoryController.class,
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
class StandardCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StandardCategoryService standardCategoryService;

    @Test
    @DisplayName("표준 카테고리 목록 응답은 감싸는 객체 없는 배열과 기존 필드명 유지")
    void getStandardCategoriesKeepsResponseContract() throws Exception {
        // given
        given(standardCategoryService.getStandardCategories()).willReturn(List.of(
            new StandardCategoryResponse(1L, "NOVEL", "소설", "소설", null, 1, 2),
            new StandardCategoryResponse(3L, "NOVEL_KR", "한국소설", "소설>한국소설", 1L, 2, 1)
        ));

        // when, then
        mockMvc.perform(get("/api/categories/standard"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0]", aMapWithSize(7)))
            .andExpect(jsonPath("$[0].standardCategoryId").value(1))
            .andExpect(jsonPath("$[0].categoryCode").value("NOVEL"))
            .andExpect(jsonPath("$[0].categoryName").value("소설"))
            .andExpect(jsonPath("$[0].categoryPath").value("소설"))
            .andExpect(jsonPath("$[0].parentCategoryId").value(nullValue()))
            .andExpect(jsonPath("$[0].depth").value(1))
            .andExpect(jsonPath("$[0].sortOrder").value(2))
            .andExpect(jsonPath("$[1].parentCategoryId").value(1));
    }
}
