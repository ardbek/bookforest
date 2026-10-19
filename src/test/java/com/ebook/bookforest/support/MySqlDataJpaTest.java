package com.ebook.bookforest.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * 실제 MySQL(Testcontainers)을 쓰는 JPA 테스트 공통 어노테이션
 * 내장 DB로 바꾸지 않고 운영과 같은 스키마의 컨테이너 DB에 연결, dbTest 작업으로만 실행
 * 대상 Service는 테스트 클래스에서 별도 @Import로 등록
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestContainerConfig.class)
@Tag("db")
public @interface MySqlDataJpaTest {
}
