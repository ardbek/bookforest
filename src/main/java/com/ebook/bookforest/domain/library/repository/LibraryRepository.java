package com.ebook.bookforest.domain.library.repository;

import com.ebook.bookforest.domain.library.entity.Library;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * 도서관 정보 조회 Repository
 */
public interface LibraryRepository extends JpaRepository<Library, Long> {

    Optional<Library> findByIdAndVisibleTrue(Long id);

    /**
     * 플랫폼별 도서관 조회
     * @param platformId
     * @param pageable
     * @return
     */
    @EntityGraph(attributePaths = "platform")
    Page<Library> findAllByPlatformIdAndVisibleTrueOrderByLibraryNameAsc(
        Long platformId,
        Pageable pageable
    );

    /**
     * 플랫폼별 도서관 조회(검색어)
     * @param platformId
     * @param libraryName
     * @param pageable
     * @return
     */
    @EntityGraph(attributePaths = "platform")
    Page<Library> findAllByPlatformIdAndVisibleTrueAndLibraryNameContainingIgnoreCaseOrderByLibraryNameAsc(
        Long platformId,
        String libraryName,
        Pageable pageable
    );
}
