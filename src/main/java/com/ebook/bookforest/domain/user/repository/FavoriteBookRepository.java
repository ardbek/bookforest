package com.ebook.bookforest.domain.user.repository;

import com.ebook.bookforest.domain.user.entity.FavoriteBook;
import java.util.List;
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
}
