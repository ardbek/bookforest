package com.ebook.bookforest.domain.category.service;

import com.ebook.bookforest.domain.category.dto.StandardCategoryResponse;
import com.ebook.bookforest.domain.category.repository.StandardCategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 표준 카테고리 조회 서비스
 * 표준 카테고리는 시드 SQL이 등록하므로 조회만 담당
 */
@Service
@RequiredArgsConstructor
public class StandardCategoryService {

    private final StandardCategoryRepository standardCategoryRepository;

    /**
     * 표준 카테고리 목록 조회(활성)
     * 상위 카테고리가 먼저 오도록 깊이 오름차순, 같은 깊이에서는 정렬 순서 오름차순
     */
    @Transactional(readOnly = true)
    public List<StandardCategoryResponse> getStandardCategories() {
        return standardCategoryRepository.findAllByActiveTrueOrderByDepthAscSortOrderAsc()
            .stream()
            .map(StandardCategoryResponse::from)
            .toList();
    }
}
