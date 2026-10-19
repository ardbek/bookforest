package com.ebook.bookforest.domain.searchkeyword.service;

import com.ebook.bookforest.domain.searchkeyword.config.SearchKeywordProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 다른 패키지 테스트에서 SearchKeywordService를 만들기 위한 테스트 도우미
 * Redis 연결과 시계를 직접 지정하는 패키지 전용 생성자 사용
 */
public final class SearchKeywordServiceFixture {

    private SearchKeywordServiceFixture() {
    }

    public static SearchKeywordService create(SearchKeywordProperties properties, StringRedisTemplate redis, Clock clock) {
        return new SearchKeywordService(properties, redis, JsonMapper.builder().build(), new SimpleMeterRegistry(), clock);
    }
}
