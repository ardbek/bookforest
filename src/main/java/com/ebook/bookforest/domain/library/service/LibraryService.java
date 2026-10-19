package com.ebook.bookforest.domain.library.service;

import com.ebook.bookforest.domain.library.dto.LibraryPageResponse;
import com.ebook.bookforest.domain.library.dto.LibraryResponse;
import com.ebook.bookforest.domain.library.entity.Library;
import com.ebook.bookforest.domain.library.exception.LibraryErrorCode;
import com.ebook.bookforest.domain.library.exception.LibraryException;
import com.ebook.bookforest.domain.library.repository.LibraryRepository;
import com.ebook.bookforest.domain.platform.dto.PlatformInfo;
import com.ebook.bookforest.domain.platform.exception.PlatformException;
import com.ebook.bookforest.domain.platform.service.PlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 무료 전자도서관 조회 서비스
 * 도서관 데이터는 수집기가 등록하므로 조회만 담당
 */
@Service
@RequiredArgsConstructor
public class LibraryService {

    private final LibraryRepository libraryRepository;
    private final PlatformService platformService;

    /**
     * 도서관 검색(플랫폼별, 노출 도서관, 이름순)
     *
     * <p>검색어별 조회 범위
     * <ul>
     *   <li>null 또는 공백: 플랫폼의 전체 노출 도서관</li>
     *   <li>그 외: 앞뒤 공백 제거 후 도서관명 부분 일치, 대소문자 무시</li>
     * </ul>
     *
     * @throws PlatformException 플랫폼이 없는 경우 PLATFORM_NOT_FOUND
     * @throws LibraryException  무료 도서관을 지원하지 않는 플랫폼인 경우 LIBRARY_NOT_SUPPORTED
     */
    @Transactional(readOnly = true)
    public LibraryPageResponse searchLibraries(Long platformId, String searchKeyword, Pageable pageable) {
        PlatformInfo platform = platformService.getPlatformInfo(platformId);
        validateLibrarySupported(platform);

        String normalizedKeyword = normalizeKeyword(searchKeyword);
        Page<Library> libraries = normalizedKeyword == null
            ? libraryRepository.findVisibleByPlatform(platformId, pageable)
            : libraryRepository.findVisibleByPlatformAndName(platformId, normalizedKeyword, pageable);

        return LibraryPageResponse.from(libraries.map(library -> LibraryResponse.from(library, false)));
    }

    /**
     * 도서관 엔티티 참조(연관관계 설정용, 노출 도서관)
     * 받는 쪽은 엔티티 상태 변경이나 필드 기반 판단 금지
     *
     * @throws LibraryException 도서관이 없거나 숨김 상태인 경우 LIBRARY_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public Library getLibraryReference(Long libraryId) {
        return libraryRepository.findByIdAndVisibleTrue(libraryId)
            .orElseThrow(() -> new LibraryException(LibraryErrorCode.LIBRARY_NOT_FOUND));
    }

    private void validateLibrarySupported(PlatformInfo platform) {
        if (!platform.supportsLibrary()) {
            throw new LibraryException(LibraryErrorCode.LIBRARY_NOT_SUPPORTED);
        }
    }

    private String normalizeKeyword(String searchKeyword) {
        if (searchKeyword == null || searchKeyword.isBlank()) {
            return null;
        }
        return searchKeyword.trim();
    }
}
