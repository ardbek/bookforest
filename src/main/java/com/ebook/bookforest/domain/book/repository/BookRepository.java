package com.ebook.bookforest.domain.book.repository;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<BookOrigin, Long>, BookSearchRepository {

    @EntityGraph(attributePaths = "platform")
    List<BookOrigin> findAllByIsbn13RawInAndActiveTrueOrderByIdAsc(List<String> isbns);

    @EntityGraph(attributePaths = "platform")
    Optional<BookOrigin> findByPlatform_IdAndPlatformBookIdAndActiveTrue(
        Long platformId,
        String platformBookId
    );

    @EntityGraph(attributePaths = "platform")
    List<BookOrigin> findAllByIsbn13RawAndActiveTrueOrderByPlatform_IdAscIdAsc(String isbn13);

    @EntityGraph(attributePaths = "platform")
    List<BookOrigin> findAllByTitleNormAndAuthorNormAndActiveTrueOrderByPlatform_IdAscIdAsc(
        String titleNorm,
        String authorNorm
    );
}
