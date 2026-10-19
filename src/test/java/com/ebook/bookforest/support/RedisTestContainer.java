package com.ebook.bookforest.support;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 Redis 컨테이너, 운영과 같은 Redis 7.2 이미지 사용
 * 테스트 JVM 안에서 한 번만 띄우고 Testcontainers가 종료 시 정리
 */
public final class RedisTestContainer {

    private static final int REDIS_PORT = 6379;
    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(REDIS_PORT);

    private RedisTestContainer() {
    }

    /** 컨테이너 Redis에 연결한 새 연결 팩토리, 호출한 쪽이 destroy */
    public static synchronized LettuceConnectionFactory connectionFactory() {
        if (!REDIS.isRunning()) {
            REDIS.start();
        }
        LettuceConnectionFactory factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }
}
