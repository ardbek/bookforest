package com.ebook.bookforest.domain.category.controller;

import com.ebook.bookforest.domain.category.dto.StandardCategoryResponse;
import com.ebook.bookforest.domain.category.service.StandardCategoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 표준 카테고리 조회 API
 */
@RestController
@RequiredArgsConstructor
public class StandardCategoryController {

    private final StandardCategoryService standardCategoryService;

    /** 표준 카테고리 목록 조회(활성, 깊이와 정렬 순서 기준) */
    @GetMapping("/api/categories/standard")
    public List<StandardCategoryResponse> getStandardCategories() {
        return standardCategoryService.getStandardCategories();
    }
}
