package com.ebook.bookforest.domain.book.repository;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 원본 도서 Repository
 */
public interface BookRepository extends JpaRepository<BookOrigin, Long>, BookSearchRepository {

    /** 원본 도서 조회(ISBN 목록, 활성 도서, ID순), 인기 도서 목록의 도서 매칭용 */
    @EntityGraph(attributePaths = "platform")
    List<BookOrigin> findAllByIsbn13RawInAndActiveTrueOrderByIdAsc(List<String> isbns);

    /**
     * 원본 도서 목록 조회(플랫폼 도서 ID별, 활성 도서)
     * DB 유니크 키가 콘텐츠 종류까지 포함하므로 같은 ID의 전자책과 오디오북이 함께 나올 수 있음
     */
    @EntityGraph(attributePaths = "platform")
    List<BookOrigin> findAllByPlatform_IdAndPlatformBookIdAndActiveTrue(Long platformId, String platformBookId);

    /** 원본 도서 조회(ISBN 일치, 활성 도서, 플랫폼순), 관련 도서 찾기용 */
    @EntityGraph(attributePaths = "platform")
    List<BookOrigin> findAllByIsbn13RawAndActiveTrueOrderByPlatform_IdAscIdAsc(String isbn13);

    /** 원본 도서 조회(정규화 제목과 저자 일치, 활성 도서, 플랫폼순), 관련 도서 찾기용 */
    @EntityGraph(attributePaths = "platform")
    List<BookOrigin> findAllByTitleNormAndAuthorNormAndActiveTrueOrderByPlatform_IdAscIdAsc(
        String titleNorm,
        String authorNorm
    );
}
