package com.ebook.bookforest.domain.category.service;

import com.ebook.bookforest.domain.category.dto.StandardCategoryResponse;
import com.ebook.bookforest.domain.category.repository.StandardCategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StandardCategoryService {

    private final StandardCategoryRepository standardCategoryRepository;

    @Transactional(readOnly = true)
    public List<StandardCategoryResponse> getStandardCategories() {
        return standardCategoryRepository
            .findAllByActiveTrueOrderByDepthAscSortOrderAsc()
            .stream()
            .map(StandardCategoryResponse::from)
            .toList();
    }
}
