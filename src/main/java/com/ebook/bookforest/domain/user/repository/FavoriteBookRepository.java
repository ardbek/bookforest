package com.ebook.bookforest.domain.user.repository;

import com.ebook.bookforest.domain.user.entity.FavoriteBook;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteBookRepository extends JpaRepository<FavoriteBook, Long> {

    @Query("""
        select fb.book.id
        from FavoriteBook fb
        where fb.user.id = :userId
          and fb.book.id in :bookIds
        """)
    List<Long> findBookIdsByUserIdAndBookIdIn(
        @Param("userId") Long userId,
        @Param("bookIds") List<Long> bookIds
    );

    @EntityGraph(attributePaths = "book")
    Page<FavoriteBook> findAllByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("select fb.book.id from FavoriteBook fb where fb.user.id = :userId")
    List<Long> findBookIdsByUserId(@Param("userId") Long userId);

    boolean existsByUser_IdAndBook_Id(Long userId, Long bookId);

    long deleteByUser_IdAndBook_Id(Long userId, Long bookId);

    long deleteAllByUser_Id(Long userId);
}
