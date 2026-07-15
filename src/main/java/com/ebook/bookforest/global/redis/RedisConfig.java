package com.ebook.bookforest.global.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration(proxyBeanMethods = false)
public class RedisConfig {

    @Bean
    public RedisScript<Long> refreshTokenRotationScript() {
        return RedisScript.of(
            new ClassPathResource(
                "redis/rotate_refresh_token.lua"
            ),
            Long.class
        );
    }

}
