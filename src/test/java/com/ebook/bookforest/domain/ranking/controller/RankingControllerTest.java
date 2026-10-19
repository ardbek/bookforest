package com.ebook.bookforest.domain.ranking.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebook.bookforest.domain.ranking.dto.RankingResponse;
import com.ebook.bookforest.domain.ranking.enums.RankingType;
import com.ebook.bookforest.domain.ranking.service.RankingService;
import com.ebook.bookforest.domain.searchkeyword.config.SearchKeywordWebConfig;
import com.ebook.bookforest.global.security.jwt.JwtAuthenticationFilter;
import com.ebook.bookforest.global.security.jwt.JwtExceptionHandlerFilter;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.hamcrest.Matcher;
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
 * 인기, 급상승 도서 API 계약 검증
 */
@WebMvcTest(
    controllers = RankingController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {JwtAuthenticationFilter.class, JwtExceptionHandlerFilter.class, SearchKeywordWebConfig.class}
    )
)
@AutoConfigureMockMvc(addFilters = false)
class RankingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RankingService rankingService;

    @Test
    @DisplayName("인기 도서와 급상승 도서 응답은 기존 JSON 구조와 필드명 유지")
    void rankingsKeepResponseContract() throws Exception {
        // given
        RankingResponse response = new RankingResponse(
            OffsetDateTime.of(2026, 10, 15, 12, 0, 0, 0, ZoneOffset.ofHours(9)),
            LocalDate.of(2026, 10, 14),
            List.of(new RankingResponse.Book(1, 10L, "9780000000001", "자바의 정석", "남궁성", "도우출판",
                "https://cover", 42L, List.of(new RankingResponse.Platform(5L, "교보문고", "LIBRARY"))))
        );
        given(rankingService.get(RankingType.POPULAR)).willReturn(response);
        given(rankingService.get(RankingType.TRENDING)).willReturn(RankingResponse.empty());

        // when, then
        mockMvc.perform(get("/api/books/popular"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", keys("updatedAt", "sourceDate", "books")))
            .andExpect(jsonPath("$.sourceDate").value("2026-10-14"))
            .andExpect(jsonPath("$.books[0]", keys(
                "rank", "bookId", "isbn13", "title", "author", "publisher", "thumbnail", "loanCount", "platforms"
            )))
            .andExpect(jsonPath("$.books[0].platforms[0]", keys("platformId", "platformName", "platformType")));
        mockMvc.perform(get("/api/books/trending"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", keys("updatedAt", "sourceDate", "books")))
            .andExpect(jsonPath("$.books").isEmpty());
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
