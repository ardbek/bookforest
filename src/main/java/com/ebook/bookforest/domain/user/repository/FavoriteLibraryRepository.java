package com.ebook.bookforest.domain.user.repository;

import com.ebook.bookforest.domain.user.entity.FavoriteLibrary;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 즐겨찾기 도서관 Repository
 */
public interface FavoriteLibraryRepository extends JpaRepository<FavoriteLibrary, Long> {

    /** 즐겨찾기 도서관 조회(사용자별, 최근 추가순) */
    @EntityGraph(attributePaths = {"library", "library.platform"})
    Page<FavoriteLibrary> findAllByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 즐겨찾기 도서관 ID 조회(사용자별) */
    @Query("select fl.library.id from FavoriteLibrary fl where fl.user.id = :userId")
    List<Long> findLibraryIdsByUserId(@Param("userId") Long userId);

    boolean existsByUser_IdAndLibrary_Id(Long userId, Long libraryId);

    long deleteByUser_IdAndLibrary_Id(Long userId, Long libraryId);

    /** 즐겨찾기 도서관 전체 삭제(사용자별, 회원 탈퇴 시) */
    long deleteAllByUser_Id(Long userId);
}
