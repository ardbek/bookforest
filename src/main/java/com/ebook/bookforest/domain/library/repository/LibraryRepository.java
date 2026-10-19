package com.ebook.bookforest.domain.library.repository;

import com.ebook.bookforest.domain.library.entity.Library;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 무료 전자도서관 조회 Repository
 */
public interface LibraryRepository extends JpaRepository<Library, Long> {

    /** 도서관 단건 조회(노출 도서관) */
    Optional<Library> findByIdAndVisibleTrue(Long id);

    /** 도서관 조회(플랫폼별, 노출 도서관, 이름순) */
    @EntityGraph(attributePaths = "platform")
    @Query("""
        select l from Library l
        where l.platform.id = :platformId
          and l.visible = true
        order by l.libraryName asc
        """)
    Page<Library> findVisibleByPlatform(@Param("platformId") Long platformId, Pageable pageable);

    /**
     * 도서관 조회(플랫폼별, 노출 도서관, 이름 부분 일치, 이름순)
     * 기존 파생 쿼리와 같은 의미를 위해 대소문자 무시, LIKE 특수문자 이스케이프
     */
    @EntityGraph(attributePaths = "platform")
    @Query("""
        select l from Library l
        where l.platform.id = :platformId
          and l.visible = true
          and upper(l.libraryName) like upper(concat('%', :#{escape(#keyword)}, '%')) escape :#{escapeCharacter()}
        order by l.libraryName asc
        """)
    Page<Library> findVisibleByPlatformAndName(
        @Param("platformId") Long platformId,
        @Param("keyword") String keyword,
        Pageable pageable
    );
}
