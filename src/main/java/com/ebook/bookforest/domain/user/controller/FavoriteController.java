package com.ebook.bookforest.domain.user.controller;

import com.ebook.bookforest.domain.user.dto.FavoriteBookPageResponse;
import com.ebook.bookforest.domain.user.dto.FavoriteLibraryPageResponse;
import com.ebook.bookforest.domain.user.service.FavoriteService;
import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 즐겨찾기 관리
 */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /**
     * 즐겨찾기 도서 목록 조회
     * @param principal
     * @return
     */
    @GetMapping("/favorite-books")
    public FavoriteBookPageResponse getFavoriteBooks(
        @AuthenticationPrincipal JwtPrincipal principal,
        @PageableDefault(size = 8) Pageable pageable
    ) {
        return favoriteService.getFavoriteBooks(principal.userId(), pageable);
    }

    @GetMapping("/favorite-books/ids")
    public List<Long> getFavoriteBookOriginIds(@AuthenticationPrincipal JwtPrincipal principal) {
        return favoriteService.getFavoriteBookOriginIds(principal.userId());
    }

    /**
     * 즐겨찾기 도서 추가
     * @param principal
     * @param bookOriginId
     * @return
     */
    @PostMapping("/favorite-books/{bookOriginId}")
    public ResponseEntity<Void> addFavoriteBook(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long bookOriginId) {
        favoriteService.addFavoriteBook(principal.userId(), bookOriginId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 즐겨찾기 도서 삭제
     * @param principal
     * @param bookOriginId
     * @return
     */
    @DeleteMapping("/favorite-books/{bookOriginId}")
    public ResponseEntity<Void> removeFavoriteBook(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long bookOriginId) {
        favoriteService.removeFavoriteBook(principal.userId(), bookOriginId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 즐겨찾기 도서관 목록 조회
     * @param principal
     * @return
     */
    @GetMapping("/favorite-libraries")
    public FavoriteLibraryPageResponse getFavoriteLibraries(
        @AuthenticationPrincipal JwtPrincipal principal,
        @PageableDefault(size = 8) Pageable pageable
    ) {
        return favoriteService.getFavoriteLibraries(principal.userId(), pageable);
    }

    @GetMapping("/favorite-libraries/ids")
    public List<Long> getFavoriteLibraryIds(@AuthenticationPrincipal JwtPrincipal principal) {
        return favoriteService.getFavoriteLibraryIds(principal.userId());
    }

    /**
     * 즐겨찾기 도서관 추가
     * @param principal
     * @param libraryId
     * @return
     */
    @PostMapping("/favorite-libraries/{libraryId}")
    public ResponseEntity<Void> addFavoriteLibrary(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long libraryId) {
        favoriteService.addFavoriteLibrary(principal.userId(), libraryId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 즐겨찾기 도서관 삭제
     * @param principal
     * @param libraryId
     * @return
     */
    @DeleteMapping("/favorite-libraries/{libraryId}")
    public ResponseEntity<Void> removeFavoriteLibrary(@AuthenticationPrincipal JwtPrincipal principal, @PathVariable Long libraryId) {
        favoriteService.removeFavoriteLibrary(principal.userId(), libraryId);
        return ResponseEntity.noContent().build();
    }
}
