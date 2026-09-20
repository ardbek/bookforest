package com.ebook.bookforest.domain.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebook.bookforest.domain.user.dto.FavoriteBookPageResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteBookResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteLibraryPageResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteLibraryResponse;
import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.domain.user.service.FavoriteService;
import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;

class FavoriteControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long BOOK_ORIGIN_ID = 10L;
    private static final Long LIBRARY_ID = 20L;

    private FavoriteService favoriteService;
    private FavoriteController controller;
    private JwtPrincipal principal;

    @BeforeEach
    void setUp() {
        favoriteService = mock(FavoriteService.class);
        controller = new FavoriteController(favoriteService);
        principal = new JwtPrincipal(USER_ID, UserRole.ROLE_USER);
    }

    @Test
    void 즐겨찾기_도서_목록을_조회한다() {
        PageRequest pageable = PageRequest.of(0, 8);
        FavoriteBookResponse response = new FavoriteBookResponse(
            BOOK_ORIGIN_ID,
            2L,
            "YES24",
            "예스24",
            "불편한 편의점",
            "김호연",
            "나무옆의자",
            null,
            null
        );
        FavoriteBookPageResponse serviceResponse =
            new FavoriteBookPageResponse(
                new FavoriteBookPageResponse.FavoriteBookPage(
                    List.of(response),
                    1,
                    true
                )
            );
        when(favoriteService.getFavoriteBooks(USER_ID, pageable))
            .thenReturn(serviceResponse);

        FavoriteBookPageResponse result =
            controller.getFavoriteBooks(principal, pageable);

        assertEquals(serviceResponse, result);
    }

    @Test
    void 즐겨찾기_도서_ID_목록을_조회한다() {
        when(favoriteService.getFavoriteBookOriginIds(USER_ID))
            .thenReturn(List.of(BOOK_ORIGIN_ID));

        assertEquals(
            List.of(BOOK_ORIGIN_ID),
            controller.getFavoriteBookOriginIds(principal)
        );
    }

    @Test
    void 즐겨찾기_도서를_추가한다() {
        var result = controller.addFavoriteBook(principal, BOOK_ORIGIN_ID);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(favoriteService).addFavoriteBook(USER_ID, BOOK_ORIGIN_ID);
    }

    @Test
    void 즐겨찾기_도서를_해제한다() {
        var result = controller.removeFavoriteBook(principal, BOOK_ORIGIN_ID);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(favoriteService).removeFavoriteBook(USER_ID, BOOK_ORIGIN_ID);
    }

    @Test
    void 즐겨찾기_도서관_목록을_조회한다() {
        PageRequest pageable = PageRequest.of(0, 8);
        FavoriteLibraryResponse response = new FavoriteLibraryResponse(
            LIBRARY_ID,
            "서울도서관",
            "YES24",
            "예스24",
            "https://example.com/library"
        );
        FavoriteLibraryPageResponse serviceResponse =
            new FavoriteLibraryPageResponse(
                new FavoriteLibraryPageResponse.FavoriteLibraryPage(
                    List.of(response),
                    1,
                    true
                )
            );
        when(favoriteService.getFavoriteLibraries(USER_ID, pageable))
            .thenReturn(serviceResponse);

        FavoriteLibraryPageResponse result =
            controller.getFavoriteLibraries(principal, pageable);

        assertEquals(serviceResponse, result);
    }

    @Test
    void 즐겨찾기_도서관_ID_목록을_조회한다() {
        when(favoriteService.getFavoriteLibraryIds(USER_ID))
            .thenReturn(List.of(LIBRARY_ID));

        assertEquals(
            List.of(LIBRARY_ID),
            controller.getFavoriteLibraryIds(principal)
        );
    }

    @Test
    void 즐겨찾기_도서관을_추가한다() {
        var result = controller.addFavoriteLibrary(principal, LIBRARY_ID);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(favoriteService).addFavoriteLibrary(USER_ID, LIBRARY_ID);
    }

    @Test
    void 즐겨찾기_도서관을_해제한다() {
        var result = controller.removeFavoriteLibrary(principal, LIBRARY_ID);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(favoriteService).removeFavoriteLibrary(USER_ID, LIBRARY_ID);
    }
}
