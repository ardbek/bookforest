package com.ebook.bookforest.domain.category.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebook.bookforest.domain.category.dto.StandardCategoryResponse;
import com.ebook.bookforest.domain.category.entity.StandardCategory;
import com.ebook.bookforest.support.MySqlDataJpaTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 표준 카테고리 목록 조회 동작 검증
 * 내부 구조가 바뀌어도 동작을 고정하도록 실제 Repository와 컨테이너 DB 사용
 */
@MySqlDataJpaTest
@Import(StandardCategoryService.class)
class StandardCategoryServiceIntegrationTest {

    @Autowired
    private StandardCategoryService standardCategoryService;

    @Autowired
    private EntityManager entityManager;

    private StandardCategory novel;
    private StandardCategory koreanNovel;

    @BeforeEach
    void setUp() {
        novel = saveCategory("NOVEL", "소설", null, "소설", 1, 2, true);
        saveCategory("ESSAY", "에세이", null, "에세이", 1, 1, true);
        koreanNovel = saveCategory("NOVEL_KR", "한국소설", novel, "소설>한국소설", 2, 1, true);
        saveCategory("NOVEL_JP", "일본소설", novel, "소설>일본소설", 2, 2, true);
        saveCategory("OLD", "폐지된 분류", null, "폐지된 분류", 1, 0, false);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("활성 표준 카테고리만 깊이, 정렬 순서 기준으로 반환")
    void getStandardCategoriesReturnsActiveCategoriesOrderedByDepthAndSortOrder() {
        // when
        List<StandardCategoryResponse> categories = standardCategoryService.getStandardCategories();

        // then
        assertThat(categories)
            .extracting(StandardCategoryResponse::categoryCode)
            .containsExactly("ESSAY", "NOVEL", "NOVEL_KR", "NOVEL_JP");
    }

    @Test
    @DisplayName("응답 항목은 카테고리 정보와 부모 카테고리 ID 포함, 최상위는 부모 ID null")
    void getStandardCategoriesMapsCategoryFields() {
        // when
        List<StandardCategoryResponse> categories = standardCategoryService.getStandardCategories();

        // then
        assertThat(categories.get(1)).isEqualTo(new StandardCategoryResponse(
            novel.getId(), "NOVEL", "소설", "소설", null, 1, 2
        ));
        assertThat(categories.get(2)).isEqualTo(new StandardCategoryResponse(
            koreanNovel.getId(), "NOVEL_KR", "한국소설", "소설>한국소설", novel.getId(), 2, 1
        ));
    }

    private StandardCategory saveCategory(
        String categoryCode,
        String categoryName,
        StandardCategory parentCategory,
        String categoryPath,
        int depth,
        int sortOrder,
        boolean active
    ) {
        StandardCategory category = StandardCategory.builder()
            .categoryCode(categoryCode)
            .categoryName(categoryName)
            .parentCategory(parentCategory)
            .categoryPath(categoryPath)
            .depth(depth)
            .sortOrder(sortOrder)
            .build();
        ReflectionTestUtils.setField(category, "active", active);
        entityManager.persist(category);
        return category;
    }
}
