package com.ebook.bookforest.domain.platform.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebook.bookforest.domain.platform.dto.PlatformListResponse;
import com.ebook.bookforest.domain.platform.dto.PlatformResponse;
import com.ebook.bookforest.domain.platform.service.PlatformService;
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
 * 플랫폼 목록 API 계약 검증
 * 보안 필터와 검색어 집계 설정은 제외하고 URL, 파라미터 바인딩, 응답 JSON 형식만 확인
 */
@WebMvcTest(
    controllers = PlatformController.class,
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
class PlatformControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlatformService platformService;

    @Test
    @DisplayName("플랫폼 목록 응답은 기존 JSON 필드명과 자료형 유지")
    void getPlatformsKeepsResponseContract() throws Exception {
        // given
        given(platformService.getPlatforms(true)).willReturn(new PlatformListResponse(List.of(
            new PlatformResponse(1L, "MILLIE", "밀리의 서재", null, true)
        )));

        // when, then
        mockMvc.perform(get("/api/platform").param("isSubscribe", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", aMapWithSize(1)))
            .andExpect(jsonPath("$.platforms[0]", aMapWithSize(5)))
            .andExpect(jsonPath("$.platforms[0].id").value(1))
            .andExpect(jsonPath("$.platforms[0].platformCode").value("MILLIE"))
            .andExpect(jsonPath("$.platforms[0].platformName").value("밀리의 서재"))
            .andExpect(jsonPath("$.platforms[0].logoUrl").value(nullValue()))
            .andExpect(jsonPath("$.platforms[0].subscribe").value(true));
    }

    @Test
    @DisplayName("isSubscribe 파라미터가 없으면 구독 여부 미지정으로 조회")
    void getPlatformsPassesNullWhenParameterMissing() throws Exception {
        // given
        given(platformService.getPlatforms(null)).willReturn(new PlatformListResponse(List.of()));

        // when
        mockMvc.perform(get("/api/platform"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.platforms").isEmpty());

        // then
        then(platformService).should().getPlatforms(null);
    }

    @Test
    @DisplayName("isSubscribe 값이 boolean이 아니면 invalid_request 오류 응답")
    void getPlatformsRejectsInvalidParameter() throws Exception {
        // when
        mockMvc.perform(get("/api/platform").param("isSubscribe", "maybe"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("invalid_request"));

        // then
        then(platformService).should(never()).getPlatforms(any());
    }
}
