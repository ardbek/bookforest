package com.ebook.bookforest.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 생성, 수정 시각 자동 기록 활성화(BaseTimeEntity용)
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
