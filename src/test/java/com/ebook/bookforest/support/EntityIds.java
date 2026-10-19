package com.ebook.bookforest.support;

import org.springframework.test.util.ReflectionTestUtils;

/**
 * 영속화 없이 엔티티 식별자를 지정하는 테스트 도우미
 * 운영 코드에 id 설정 경로를 열지 않기 위한 용도
 */
public final class EntityIds {

    private EntityIds() {
    }

    /** 엔티티의 id 필드 지정 후 같은 엔티티 반환 */
    public static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
