package com.ebook.bookforest.domain.library.service;

import com.ebook.bookforest.domain.library.dto.LibraryPageResponse;
import com.ebook.bookforest.domain.library.dto.LibraryResponse;
import com.ebook.bookforest.domain.library.entity.Library;
import com.ebook.bookforest.domain.library.exception.LibraryErrorCode;
import com.ebook.bookforest.domain.library.exception.LibraryException;
import com.ebook.bookforest.domain.library.repository.LibraryRepository;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.domain.platform.repository.PlatformRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LibraryService {

    private final LibraryRepository libraryRepository;
    private final PlatformRepository platformRepository;

    /**
     * 도서관 목록 조회
     * @param platformId
     * @param searchKeyword
     * @param pageable
     * @return
     */
    @Transactional(readOnly = true)
    public LibraryPageResponse getLibraries(
        Long platformId,
        String searchKeyword,
        Pageable pageable
    ) {
        Platform platform = platformRepository.findById(platformId)
            .orElseThrow(() -> new LibraryException(LibraryErrorCode.PLATFORM_NOT_FOUND));

        if (!platform.isSupportsLibrary()) {
            throw new LibraryException(LibraryErrorCode.LIBRARY_NOT_SUPPORTED);
        }

        String normalizedKeyword = normalizeKeyword(searchKeyword);
        Page<Library> libraries = normalizedKeyword == null
            ? libraryRepository.findAllByPlatformIdAndVisibleTrueOrderByLibraryNameAsc(
                platformId,
                pageable
            )
            : libraryRepository.findAllByPlatformIdAndVisibleTrueAndLibraryNameContainingIgnoreCaseOrderByLibraryNameAsc(
                platformId,
                normalizedKeyword,
                pageable
            );

        return LibraryPageResponse.from(
            libraries.map(library -> LibraryResponse.from(library, false))
        );
    }

    /**
     * 검색어 정규화
     * @param searchKeyword
     * @return
     */
    private String normalizeKeyword(String searchKeyword) {
        if (searchKeyword == null || searchKeyword.isBlank()) {
            return null;
        }
        return searchKeyword.trim();
    }
}
