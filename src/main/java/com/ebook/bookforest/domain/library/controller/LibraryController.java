package com.ebook.bookforest.domain.library.controller;

import com.ebook.bookforest.domain.library.dto.LibraryPageResponse;
import com.ebook.bookforest.domain.library.service.LibraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 무료 전자도서관 조회 API
 */
@RestController
@RequestMapping("/api/library")
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService libraryService;

    /**
     * 도서관 검색(플랫폼별, 노출 도서관, 이름순)
     *
     * @param searchKeyword 도서관명 부분 일치 검색어, 없거나 공백이면 전체 도서관
     */
    @GetMapping
    public LibraryPageResponse searchLibraries(
        @RequestParam Long platformId,
        @RequestParam(required = false) String searchKeyword,
        @PageableDefault(size = 10) Pageable pageable
    ) {
        return libraryService.searchLibraries(platformId, searchKeyword, pageable);
    }
}
