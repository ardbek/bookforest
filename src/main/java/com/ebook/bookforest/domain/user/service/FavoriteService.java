package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.exception.BookException;
import com.ebook.bookforest.domain.book.service.BookService;
import com.ebook.bookforest.domain.library.entity.Library;
import com.ebook.bookforest.domain.library.exception.LibraryException;
import com.ebook.bookforest.domain.library.service.LibraryService;
import com.ebook.bookforest.domain.user.dto.FavoriteBookPageResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteBookResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteLibraryPageResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteLibraryResponse;
import com.ebook.bookforest.domain.user.entity.FavoriteBook;
import com.ebook.bookforest.domain.user.entity.FavoriteLibrary;
import com.ebook.bookforest.domain.user.entity.User;
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

/**
 * 사용자의 즐겨찾기 도서, 도서관 관리 서비스
 * 모든 기능은 활성 사용자 전용, 탈퇴했거나 없는 사용자는 UserException USER_NOT_FOUND
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserRepository userRepository;
    private final BookService bookService;
    private final LibraryService libraryService;
    private final FavoriteBookRepository favoriteBookRepository;
    private final FavoriteLibraryRepository favoriteLibraryRepository;

    /** 즐겨찾기 도서 목록 조회(최근 추가순) */
    @Transactional(readOnly = true)
    public FavoriteBookPageResponse getFavoriteBooks(Long userId, Pageable pageable) {
        validateActiveUser(userId);

        return FavoriteBookPageResponse.from(
            favoriteBookRepository.findAllByUser_IdOrderByCreatedAtDesc(userId, pageable)
                .map(FavoriteBookResponse::from)
        );
    }

    /** 즐겨찾기 도서의 원본 도서 ID 목록 조회 */
    @Transactional(readOnly = true)
    public List<Long> getFavoriteBookOriginIds(Long userId) {
        validateActiveUser(userId);
        return favoriteBookRepository.findBookOriginIdsByUserId(userId);
    }

    /**
     * 즐겨찾기 도서 추가
     *
     * @throws BookException 도서가 없거나 비활성인 경우 BOOK_NOT_FOUND
     */
    @Transactional
    public void addFavoriteBook(Long userId, Long bookOriginId) {
        User user = getActiveUser(userId);
        BookOrigin bookOrigin = bookService.getBookOriginReference(bookOriginId);

        if (favoriteBookRepository.existsByUser_IdAndBookOrigin_Id(userId, bookOriginId)) {
            return;
        }

        favoriteBookRepository.save(FavoriteBook.create(user, bookOrigin));
    }

    /** 즐겨찾기 도서 해제 */
    @Transactional
    public void removeFavoriteBook(Long userId, Long bookOriginId) {
        validateActiveUser(userId);
        favoriteBookRepository.deleteByUser_IdAndBookOrigin_Id(userId, bookOriginId);
    }

    /** 즐겨찾기 도서관 목록 조회(최근 추가순) */
    @Transactional(readOnly = true)
    public FavoriteLibraryPageResponse getFavoriteLibraries(Long userId, Pageable pageable) {
        validateActiveUser(userId);

        return FavoriteLibraryPageResponse.from(
            favoriteLibraryRepository.findAllByUser_IdOrderByCreatedAtDesc(userId, pageable)
                .map(FavoriteLibraryResponse::from)
        );
    }

    /** 즐겨찾기 도서관 ID 목록 조회 */
    @Transactional(readOnly = true)
    public List<Long> getFavoriteLibraryIds(Long userId) {
        validateActiveUser(userId);
        return favoriteLibraryRepository.findLibraryIdsByUserId(userId);
    }

    /**
     * 즐겨찾기 도서관 추가
     *
     * @throws LibraryException 도서관이 없거나 숨김 상태인 경우 LIBRARY_NOT_FOUND
     */
    @Transactional
    public void addFavoriteLibrary(Long userId, Long libraryId) {
        User user = getActiveUser(userId);
        Library library = libraryService.getLibraryReference(libraryId);

        if (favoriteLibraryRepository.existsByUser_IdAndLibrary_Id(userId, libraryId)) {
            return;
        }

        favoriteLibraryRepository.save(FavoriteLibrary.create(user, library));
    }

    /** 즐겨찾기 도서관 해제 */
    @Transactional
    public void removeFavoriteLibrary(Long userId, Long libraryId) {
        validateActiveUser(userId);
        favoriteLibraryRepository.deleteByUser_IdAndLibrary_Id(userId, libraryId);
    }

    private void validateActiveUser(Long userId) {
        if (!userRepository.existsByIdAndActiveTrue(userId)) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    private User getActiveUser(Long userId) {
        return userRepository.findByIdAndActiveTrue(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }
}
