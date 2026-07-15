package com.ebook.bookforest.domain.auth.repository;

import com.ebook.bookforest.domain.auth.model.RefreshTokenSession;
import com.ebook.bookforest.domain.auth.model.RefreshTokenRotationCommand;
import com.ebook.bookforest.domain.auth.model.RefreshTokenRotationStatus;
import java.util.Optional;

public interface RefreshTokenStore {

    void save(RefreshTokenSession session);

    Optional<RefreshTokenSession> find(Long userId, String sessionId);

    RefreshTokenRotationStatus rotate(RefreshTokenRotationCommand command);

    void delete(Long userId, String sessionId);

    void deleteAllByUserId(Long userId);

}
