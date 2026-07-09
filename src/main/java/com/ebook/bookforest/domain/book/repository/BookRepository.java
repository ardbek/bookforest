package com.ebook.bookforest.domain.book.repository;

import com.ebook.bookforest.domain.book.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long>, BookSearchRepository {
}
