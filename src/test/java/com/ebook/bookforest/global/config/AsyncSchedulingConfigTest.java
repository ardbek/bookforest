package com.ebook.bookforest.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebook.bookforest.domain.user.config.SocialUnlinkExecutorConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기, 스케줄링 활성화와 소셜 연동 해제 실행기 설정 검증
 * 설정 클래스 위치를 옮겨도 빈 이름과 스레드 풀 값이 그대로인지 확인
 */
class AsyncSchedulingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(AsyncSchedulingConfig.class, SocialUnlinkExecutorConfig.class);

    @Test
    @DisplayName("비동기와 스케줄링 처리기 등록")
    void enablesAsyncAndScheduling() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AsyncAnnotationBeanPostProcessor.class);
            assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
        });
    }

    @Test
    @DisplayName("소셜 연동 해제 실행기의 빈 이름과 스레드 풀 값 유지")
    void socialUnlinkExecutorKeepsPoolSettings() {
        contextRunner.run(context -> {
            ThreadPoolTaskExecutor executor = context.getBean("socialUnlinkExecutor", ThreadPoolTaskExecutor.class);

            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(4);
            assertThat(executor.getQueueCapacity()).isEqualTo(100);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("social-unlink-");
        });
    }
}
