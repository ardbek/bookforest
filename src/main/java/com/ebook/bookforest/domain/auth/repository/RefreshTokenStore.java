package com.ebook.bookforest.domain.auth.repository;

import com.ebook.bookforest.domain.auth.model.RefreshTokenRotationCommand;
import com.ebook.bookforest.domain.auth.model.RefreshTokenRotationStatus;
import com.ebook.bookforest.domain.auth.model.RefreshTokenSession;
import java.util.Optional;

/**
 * Refresh Token 세션 저장소
 * 구현체는 Redis 기반 RedisRefreshTokenStore
 */
public interface RefreshTokenStore {

    void save(RefreshTokenSession session);

    Optional<RefreshTokenSession> find(Long userId, String sessionId);

    /** 토큰 교체, 동시 요청에도 한 번만 교체되도록 원자적으로 처리 */
    RefreshTokenRotationStatus rotate(RefreshTokenRotationCommand command);

    void delete(Long userId, String sessionId);

    /** 사용자의 모든 세션 삭제(재사용 감지, 비활성 사용자, 회원 탈퇴 시) */
    void deleteAllByUserId(Long userId);
}
