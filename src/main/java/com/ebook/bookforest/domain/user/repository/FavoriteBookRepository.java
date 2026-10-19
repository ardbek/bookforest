package com.ebook.bookforest.domain.user.repository;

import com.ebook.bookforest.domain.user.entity.FavoriteBook;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 즐겨찾기 도서 Repository
 */
public interface FavoriteBookRepository extends JpaRepository<FavoriteBook, Long> {

    /** 즐겨찾기 도서 조회(사용자별, 최근 추가순) */
    @EntityGraph(attributePaths = "bookOrigin.platform")
    Page<FavoriteBook> findAllByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 즐겨찾기 원본 도서 ID 조회(사용자별) */
    @Query("select fb.bookOrigin.id from FavoriteBook fb where fb.user.id = :userId")
    List<Long> findBookOriginIdsByUserId(@Param("userId") Long userId);

    boolean existsByUser_IdAndBookOrigin_Id(Long userId, Long bookOriginId);

    long deleteByUser_IdAndBookOrigin_Id(Long userId, Long bookOriginId);

    /** 즐겨찾기 도서 전체 삭제(사용자별, 회원 탈퇴 시) */
    long deleteAllByUser_Id(Long userId);
}
