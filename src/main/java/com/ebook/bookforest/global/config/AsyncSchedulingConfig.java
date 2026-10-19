package com.ebook.bookforest.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 비동기 실행과 스케줄러 활성화
 * 랭킹, 인기 검색어, 소셜 연동 해제 재시도 스케줄러와 소셜 연동 해제 리스너가 사용
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncSchedulingConfig {
}
