package com.ebook.bookforest.domain.library.service;

import com.ebook.bookforest.domain.library.dto.LibraryPageResponse;
import com.ebook.bookforest.domain.library.dto.LibraryResponse;
import com.ebook.bookforest.domain.library.entity.Library;
import com.ebook.bookforest.domain.library.repository.LibraryRepository;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.domain.platform.repository.PlatformRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "플랫폼을 찾을 수 없습니다: " + platformId
            ));

        if (!platform.isSupportsLibrary()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "전자도서관을 지원하지 않는 플랫폼입니다: " + platformId
            );
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
