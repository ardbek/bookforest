package com.ebook.bookforest.domain.category.controller;

import com.ebook.bookforest.domain.category.dto.StandardCategoryResponse;
import com.ebook.bookforest.domain.category.service.StandardCategoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StandardCategoryController {

    private final StandardCategoryService standardCategoryService;

    @GetMapping({"/api/categories/standard"})
    public List<StandardCategoryResponse> getStandardCategories() {
        return standardCategoryService.getStandardCategories();
    }
}
