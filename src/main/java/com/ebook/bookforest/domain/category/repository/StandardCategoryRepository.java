package com.ebook.bookforest.domain.category.repository;

import com.ebook.bookforest.domain.category.entity.StandardCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 표준 카테고리 조회 Repository
 */
public interface StandardCategoryRepository extends JpaRepository<StandardCategory, Long> {

    /** 표준 카테고리 조회(활성, 깊이와 정렬 순서 오름차순) */
    List<StandardCategory> findAllByActiveTrueOrderByDepthAscSortOrderAsc();
}
