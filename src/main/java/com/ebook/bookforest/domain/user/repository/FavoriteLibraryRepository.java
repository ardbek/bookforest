package com.ebook.bookforest.domain.user.repository;

import com.ebook.bookforest.domain.user.entity.FavoriteLibrary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteLibraryRepository extends JpaRepository<FavoriteLibrary, Long> {

    /* 즐겨찾기 도서관 전체 삭제 */
    long deleteAllByUser_Id(Long userId);

}
