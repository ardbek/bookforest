package com.ebook.bookforest.domain.category.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StandardCategoryTest {

    @Test
    @DisplayName("깊이와 정렬 순서를 지정하지 않은 표준 카테고리는 깊이 1, 정렬 순서 0, 활성 상태")
    void newStandardCategoryHasDefaultState() {
        // when
        StandardCategory category = StandardCategory.builder()
            .categoryCode("NOVEL")
            .categoryName("소설")
            .build();

        // then
        assertThat(category.getDepth()).isEqualTo(1);
        assertThat(category.getSortOrder()).isZero();
        assertThat(category.isActive()).isTrue();
        assertThat(category.getParentCategory()).isNull();
    }
}
