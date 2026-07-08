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
 * 도서관 Controller
 */
@RestController
@RequestMapping("/api/library")
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService libraryService;

    /**
     * 도서관 목록 조회
     * @param platformId
     * @param searchKeyword
     * @param pageable
     * @return
     */
    @GetMapping
    public LibraryPageResponse getLibraries(
        @RequestParam Long platformId,
        @RequestParam(required = false) String searchKeyword,
        @PageableDefault(size = 10) Pageable pageable
    ) {
        return libraryService.getLibraries(platformId, searchKeyword, pageable);
    }
}
