package com.ebook.bookforest.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.repository.BookRepository;
import com.ebook.bookforest.domain.library.entity.Library;
import com.ebook.bookforest.domain.library.repository.LibraryRepository;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.domain.user.dto.FavoriteBookPageResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteBookResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteLibraryPageResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteLibraryResponse;
import com.ebook.bookforest.domain.user.entity.FavoriteBook;
import com.ebook.bookforest.domain.user.entity.FavoriteLibrary;
import com.ebook.bookforest.domain.user.entity.User;
import com.ebook.bookforest.domain.user.exception.FavoriteErrorCode;
import com.ebook.bookforest.domain.user.exception.FavoriteException;
import com.ebook.bookforest.domain.user.repository.FavoriteBookRepository;
import com.ebook.bookforest.domain.user.repository.FavoriteLibraryRepository;
import com.ebook.bookforest.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long BOOK_ORIGIN_ID = 10L;
    private static final Long LIBRARY_ID = 20L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private LibraryRepository libraryRepository;

    @Mock
    private FavoriteBookRepository favoriteBookRepository;

    @Mock
    private FavoriteLibraryRepository favoriteLibraryRepository;

    @InjectMocks
    private FavoriteService favoriteService;

    private User user;
    private BookOrigin bookOrigin;
    private Library library;

    @BeforeEach
    void setUp() {
        user = User.builder()
            .id(USER_ID)
            .active(true)
            .build();
        Platform platform = Platform.builder()
            .id(2L)
            .platformCode("YES24")
            .platformName("예스24")
            .supportsLibrary(true)
            .build();
        bookOrigin = BookOrigin.builder()
            .id(BOOK_ORIGIN_ID)
            .platform(platform)
            .platformBookId("book-code")
            .titleRaw("불편한 편의점")
            .authorRaw("김호연")
            .publisherRaw("나무옆의자")
            .publishedDate(LocalDate.of(2021, 4, 20))
            .coverImgUrl("https://example.com/book.jpg")
            .titleNorm("불편한 편의점")
            .build();
        library = Library.builder()
            .id(LIBRARY_ID)
            .platform(platform)
            .libraryCode("library-code")
            .libraryName("서울도서관")
            .siteUrl("https://example.com/library")
            .visible(true)
            .build();
    }

    @Test
    void 즐겨찾기_도서_목록을_응답으로_변환한다() {
        PageRequest pageable = PageRequest.of(0, 8);
        FavoriteBook favoriteBook = FavoriteBook.builder()
            .user(user)
            .bookOrigin(bookOrigin)
            .build();
        when(userRepository.existsByIdAndActiveTrue(USER_ID))
            .thenReturn(true);
        when(favoriteBookRepository.findAllByUser_IdOrderByCreatedAtDesc(
            USER_ID,
            pageable
        )).thenReturn(new PageImpl<>(
            List.of(favoriteBook),
            pageable,
            9
        ));

        FavoriteBookPageResponse result =
            favoriteService.getFavoriteBooks(USER_ID, pageable);
        var page = result.favoriteBooks();

        assertEquals(1, page.content().size());
        assertEquals(9, page.totalElements());
        assertEquals(false, page.last());
        assertEquals(BOOK_ORIGIN_ID, page.content().get(0).bookOriginId());
        assertEquals("불편한 편의점", page.content().get(0).title());
        assertEquals("김호연", page.content().get(0).author());
    }

    @Test
    void 즐겨찾기_도서_ID_목록을_조회한다() {
        when(userRepository.existsByIdAndActiveTrue(USER_ID))
            .thenReturn(true);
        when(favoriteBookRepository.findBookOriginIdsByUserId(USER_ID))
            .thenReturn(List.of(BOOK_ORIGIN_ID));

        assertEquals(
            List.of(BOOK_ORIGIN_ID),
            favoriteService.getFavoriteBookOriginIds(USER_ID)
        );
    }

    @Test
    void 도서를_즐겨찾기에_추가한다() {
        when(userRepository.findByIdAndActiveTrue(USER_ID))
            .thenReturn(Optional.of(user));
        when(bookRepository.findById(BOOK_ORIGIN_ID))
            .thenReturn(Optional.of(bookOrigin));
        when(favoriteBookRepository.existsByUser_IdAndBookOrigin_Id(USER_ID, BOOK_ORIGIN_ID))
            .thenReturn(false);

        favoriteService.addFavoriteBook(USER_ID, BOOK_ORIGIN_ID);

        ArgumentCaptor<FavoriteBook> captor =
            ArgumentCaptor.forClass(FavoriteBook.class);
        verify(favoriteBookRepository).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());
        assertEquals(bookOrigin, captor.getValue().getBookOrigin());
    }

    @Test
    void 이미_저장한_도서는_중복으로_추가하지_않는다() {
        when(userRepository.findByIdAndActiveTrue(USER_ID))
            .thenReturn(Optional.of(user));
        when(bookRepository.findById(BOOK_ORIGIN_ID))
            .thenReturn(Optional.of(bookOrigin));
        when(favoriteBookRepository.existsByUser_IdAndBookOrigin_Id(USER_ID, BOOK_ORIGIN_ID))
            .thenReturn(true);

        favoriteService.addFavoriteBook(USER_ID, BOOK_ORIGIN_ID);

        verify(favoriteBookRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_도서는_즐겨찾기에_추가할_수_없다() {
        when(userRepository.findByIdAndActiveTrue(USER_ID))
            .thenReturn(Optional.of(user));
        when(bookRepository.findById(BOOK_ORIGIN_ID))
            .thenReturn(Optional.empty());

        FavoriteException exception = assertThrows(
            FavoriteException.class,
            () -> favoriteService.addFavoriteBook(USER_ID, BOOK_ORIGIN_ID)
        );

        assertEquals(
            FavoriteErrorCode.BOOK_NOT_FOUND,
            exception.getErrorCode()
        );
        verify(favoriteBookRepository, never()).save(any());
    }

    @Test
    void 도서_즐겨찾기를_해제한다() {
        when(userRepository.existsByIdAndActiveTrue(USER_ID))
            .thenReturn(true);

        favoriteService.removeFavoriteBook(USER_ID, BOOK_ORIGIN_ID);

        verify(favoriteBookRepository)
            .deleteByUser_IdAndBookOrigin_Id(USER_ID, BOOK_ORIGIN_ID);
    }

    @Test
    void 즐겨찾기_도서관_목록을_응답으로_변환한다() {
        PageRequest pageable = PageRequest.of(0, 8);
        FavoriteLibrary favoriteLibrary = FavoriteLibrary.builder()
            .user(user)
            .library(library)
            .build();
        when(userRepository.existsByIdAndActiveTrue(USER_ID))
            .thenReturn(true);
        when(favoriteLibraryRepository.findAllByUser_IdOrderByCreatedAtDesc(
            USER_ID,
            pageable
        )).thenReturn(new PageImpl<>(
            List.of(favoriteLibrary),
            pageable,
            9
        ));

        FavoriteLibraryPageResponse result =
            favoriteService.getFavoriteLibraries(USER_ID, pageable);
        var page = result.favoriteLibraries();

        assertEquals(1, page.content().size());
        assertEquals(9, page.totalElements());
        assertEquals(false, page.last());
        assertEquals(LIBRARY_ID, page.content().get(0).libraryId());
        assertEquals("서울도서관", page.content().get(0).libraryName());
        assertEquals("예스24", page.content().get(0).platformName());
    }

    @Test
    void 즐겨찾기_도서관_ID_목록을_조회한다() {
        when(userRepository.existsByIdAndActiveTrue(USER_ID))
            .thenReturn(true);
        when(favoriteLibraryRepository.findLibraryIdsByUserId(USER_ID))
            .thenReturn(List.of(LIBRARY_ID));

        assertEquals(
            List.of(LIBRARY_ID),
            favoriteService.getFavoriteLibraryIds(USER_ID)
        );
    }

    @Test
    void 도서관을_즐겨찾기에_추가한다() {
        when(userRepository.findByIdAndActiveTrue(USER_ID))
            .thenReturn(Optional.of(user));
        when(libraryRepository.findByIdAndVisibleTrue(LIBRARY_ID))
            .thenReturn(Optional.of(library));
        when(favoriteLibraryRepository.existsByUser_IdAndLibrary_Id(
            USER_ID,
            LIBRARY_ID
        )).thenReturn(false);

        favoriteService.addFavoriteLibrary(USER_ID, LIBRARY_ID);

        ArgumentCaptor<FavoriteLibrary> captor =
            ArgumentCaptor.forClass(FavoriteLibrary.class);
        verify(favoriteLibraryRepository).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());
        assertEquals(library, captor.getValue().getLibrary());
    }

    @Test
    void 존재하지_않거나_숨김_도서관은_즐겨찾기에_추가할_수_없다() {
        when(userRepository.findByIdAndActiveTrue(USER_ID))
            .thenReturn(Optional.of(user));
        when(libraryRepository.findByIdAndVisibleTrue(LIBRARY_ID))
            .thenReturn(Optional.empty());

        FavoriteException exception = assertThrows(
            FavoriteException.class,
            () -> favoriteService.addFavoriteLibrary(USER_ID, LIBRARY_ID)
        );

        assertEquals(
            FavoriteErrorCode.LIBRARY_NOT_FOUND,
            exception.getErrorCode()
        );
        verify(favoriteLibraryRepository, never()).save(any());
    }

    @Test
    void 도서관_즐겨찾기를_해제한다() {
        when(userRepository.existsByIdAndActiveTrue(USER_ID))
            .thenReturn(true);

        favoriteService.removeFavoriteLibrary(USER_ID, LIBRARY_ID);

        verify(favoriteLibraryRepository)
            .deleteByUser_IdAndLibrary_Id(USER_ID, LIBRARY_ID);
    }
}
