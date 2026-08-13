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

    @EntityGraph(attributePaths = "bookOrigin.platform")
    Page<FavoriteBook> findAllByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("select fb.bookOrigin.id from FavoriteBook fb where fb.user.id = :userId")
    List<Long> findBookOriginIdsByUserId(@Param("userId") Long userId);

    boolean existsByUser_IdAndBookOrigin_Id(Long userId, Long bookOriginId);

    long deleteByUser_IdAndBookOrigin_Id(Long userId, Long bookOriginId);

    long deleteAllByUser_Id(Long userId);
}
