package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.book.entity.Book;
import com.ebook.bookforest.domain.book.repository.BookRepository;
import com.ebook.bookforest.domain.library.entity.Library;
import com.ebook.bookforest.domain.library.repository.LibraryRepository;
import com.ebook.bookforest.domain.user.dto.FavoriteBookPageResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteBookResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteLibraryPageResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteLibraryResponse;
import com.ebook.bookforest.domain.user.entity.FavoriteBook;
import com.ebook.bookforest.domain.user.entity.FavoriteLibrary;
import com.ebook.bookforest.domain.user.entity.User;
import com.ebook.bookforest.domain.user.exception.FavoriteErrorCode;
import com.ebook.bookforest.domain.user.exception.FavoriteException;
import com.ebook.bookforest.domain.user.exception.UserErrorCode;
import com.ebook.bookforest.domain.user.exception.UserException;
import com.ebook.bookforest.domain.user.repository.FavoriteBookRepository;
import com.ebook.bookforest.domain.user.repository.FavoriteLibraryRepository;
import com.ebook.bookforest.domain.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final LibraryRepository libraryRepository;
    private final FavoriteBookRepository favoriteBookRepository;
    private final FavoriteLibraryRepository favoriteLibraryRepository;

    /**
     * 즐겨찾기 도서 조회
     * @param userId
     * @return
     */
    @Transactional(readOnly = true)
    public FavoriteBookPageResponse getFavoriteBooks(Long userId, Pageable pageable) {
        validateActiveUser(userId);

        return FavoriteBookPageResponse.from(
            favoriteBookRepository
                .findAllByUser_IdOrderByCreatedAtDesc(userId, pageable)
                .map(FavoriteBookResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public List<Long> getFavoriteBookIds(Long userId) {
        validateActiveUser(userId);
        return favoriteBookRepository.findBookIdsByUserId(userId);
    }

    /**
     * 즐겨찾기 도서 추가
     * @param userId
     * @param bookId
     */
    @Transactional
    public void addFavoriteBook(Long userId, Long bookId) {
        User user = getActiveUser(userId);
        Book book = bookRepository.findById(bookId)
            .orElseThrow(() -> new FavoriteException(
                FavoriteErrorCode.BOOK_NOT_FOUND
            ));

        if (favoriteBookRepository.existsByUser_IdAndBook_Id(userId, bookId)) {
            return;
        }

        favoriteBookRepository.save(FavoriteBook.builder()
            .user(user)
            .book(book)
            .build());
    }

    /**
     * 즐겨찾기 도서 제거
     * @param userId
     * @param bookId
     */
    @Transactional
    public void removeFavoriteBook(Long userId, Long bookId) {
        validateActiveUser(userId);
        favoriteBookRepository.deleteByUser_IdAndBook_Id(userId, bookId);
    }

    /**
     * 즐겨찾기 도서관 목록 조회
     * @param userId
     * @return
     */
    @Transactional(readOnly = true)
    public FavoriteLibraryPageResponse getFavoriteLibraries(Long userId, Pageable pageable) {
        validateActiveUser(userId);

        return FavoriteLibraryPageResponse.from(
            favoriteLibraryRepository
                .findAllByUser_IdOrderByCreatedAtDesc(userId, pageable)
                .map(FavoriteLibraryResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public List<Long> getFavoriteLibraryIds(Long userId) {
        validateActiveUser(userId);
        return favoriteLibraryRepository.findLibraryIdsByUserId(userId);
    }

    /**
     * 즐겨찾기 도서관 추가
     * @param userId
     * @param libraryId
     */
    @Transactional
    public void addFavoriteLibrary(Long userId, Long libraryId) {
        User user = getActiveUser(userId);
        Library library = libraryRepository.findByIdAndVisibleTrue(libraryId)
            .orElseThrow(() -> new FavoriteException(FavoriteErrorCode.LIBRARY_NOT_FOUND));

        if (favoriteLibraryRepository.existsByUser_IdAndLibrary_Id(userId, libraryId)) {
            return;
        }

        favoriteLibraryRepository.save(FavoriteLibrary.builder()
            .user(user)
            .library(library)
            .build());
    }

    /**
     * 즐겨찾기 도서관 제거
     * @param userId
     * @param libraryId
     */
    @Transactional
    public void removeFavoriteLibrary(Long userId, Long libraryId) {
        validateActiveUser(userId);
        favoriteLibraryRepository.deleteByUser_IdAndLibrary_Id(userId, libraryId);
    }

    /**
     * 활성화된 회원인지 검증
     * @param userId
     */
    private void validateActiveUser(Long userId) {
        if (!userRepository.existsByIdAndActiveTrue(userId)) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    /**
     * 활성화된 회원 조회
     * @param userId
     * @return
     */
    private User getActiveUser(Long userId) {
        return userRepository.findByIdAndActiveTrue(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }
}
