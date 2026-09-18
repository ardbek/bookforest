package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.user.repository.FavoriteBookRepository;
import com.ebook.bookforest.domain.user.repository.FavoriteLibraryRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteLookupService {
    private final FavoriteBookRepository favoriteBookRepository;
    private final FavoriteLibraryRepository favoriteLibraryRepository;

    // 조회 화면에 표시할 즐겨찾기 도서 ID를 반환하며 익명 사용자는 빈 집합으로 처리한다.
    @Transactional(readOnly = true)
    public Set<Long> findBookOriginIds(Long userId) {
        return userId == null
            ? Set.of()
            : new LinkedHashSet<>(favoriteBookRepository.findBookOriginIdsByUserId(userId));
    }

    // 조회 화면에 표시할 즐겨찾기 도서관 ID를 반환하며 익명 사용자는 빈 집합으로 처리한다.
    @Transactional(readOnly = true)
    public Set<Long> findLibraryIds(Long userId) {
        return userId == null
            ? Set.of()
            : new LinkedHashSet<>(favoriteLibraryRepository.findLibraryIdsByUserId(userId));
    }
}
