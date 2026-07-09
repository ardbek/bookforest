package com.ebook.bookforest.domain.category.repository;

import com.ebook.bookforest.domain.category.entity.StandardCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StandardCategoryRepository extends JpaRepository<StandardCategory, Long> {

    List<StandardCategory> findAllByActiveTrueOrderByDepthAscSortOrderAsc();
}
