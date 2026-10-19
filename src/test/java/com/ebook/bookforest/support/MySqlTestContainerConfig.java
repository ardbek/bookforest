package com.ebook.bookforest.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * 운영과 같은 버전과 스키마를 쓰는 테스트용 MySQL 컨테이너 설정
 * 스키마 파일은 ngram 파서 지정이 조건부 주석에 있어 컨테이너 초기화 폴더에서 mysql 클라이언트로 실행
 */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestContainerConfig {

    private static final String MYSQL_IMAGE = "mysql:8.0.42";
    private static final String SCHEMA_RESOURCE = "db/bookforest_schema.sql";
    private static final String INIT_SCRIPT_PATH = "/docker-entrypoint-initdb.d/bookforest_schema.sql";

    @Bean
    @ServiceConnection
    MySQLContainer mySqlContainer() {
        return new MySQLContainer(DockerImageName.parse(MYSQL_IMAGE))
            .withCopyFileToContainer(MountableFile.forClasspathResource(SCHEMA_RESOURCE), INIT_SCRIPT_PATH);
    }
}
