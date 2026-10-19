package com.ebook.bookforest.domain.user.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 소셜 연동 해제 비동기 실행기 설정
 * 외부 API 호출이 요청 스레드와 다른 비동기 작업을 막지 않도록 전용 스레드 풀 사용
 */
@Configuration
public class SocialUnlinkExecutorConfig {

    @Bean(name = "socialUnlinkExecutor")
    public Executor socialUnlinkExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("social-unlink-");
        executor.initialize();
        return executor;
    }
}
