package com.ebook.bookforest.domain.searchkeyword.config;

import com.ebook.bookforest.domain.searchkeyword.service.SearchKeywordService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 검색 API에 검색 요청 전처리 등록
 */
@Configuration
@RequiredArgsConstructor
public class SearchKeywordWebConfig implements WebMvcConfigurer {

    private final SearchKeywordService searchKeywordService;
    private final SearchKeywordProperties properties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SearchRequestInterceptor(searchKeywordService, properties))
            .addPathPatterns("/api/book/search", "/api/book/search/platforms");
    }
}
