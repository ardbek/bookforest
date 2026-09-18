package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.user.repository.FavoriteBookRepository;
import com.ebook.bookforest.domain.user.repository.FavoriteLibraryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FavoriteLookupServiceTest {
    private final FavoriteBookRepository books = mock(FavoriteBookRepository.class);
    private final FavoriteLibraryRepository libraries = mock(FavoriteLibraryRepository.class);
    private final FavoriteLookupService service = new FavoriteLookupService(books, libraries);

    @Test
    void anonymousLookupDoesNotReadRepositories() {
        assertTrue(service.findBookOriginIds(null).isEmpty());
        assertTrue(service.findLibraryIds(null).isEmpty());
        verifyNoInteractions(books, libraries);
    }

    @Test
    void userLookupPreservesDistinctIdsInRepositoryOrder() {
        when(books.findBookOriginIdsByUserId(7L)).thenReturn(List.of(3L, 1L, 3L));
        when(libraries.findLibraryIdsByUserId(7L)).thenReturn(List.of(9L, 2L, 9L));
        assertEquals(List.of(3L, 1L), List.copyOf(service.findBookOriginIds(7L)));
        assertEquals(List.of(9L, 2L), List.copyOf(service.findLibraryIds(7L)));
        verify(books).findBookOriginIdsByUserId(7L);
        verify(libraries).findLibraryIdsByUserId(7L);
        verifyNoMoreInteractions(books, libraries);
    }
}
