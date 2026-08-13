package com.ebook.bookforest.domain.book.repository;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<BookOrigin, Long>, BookSearchRepository {
}
