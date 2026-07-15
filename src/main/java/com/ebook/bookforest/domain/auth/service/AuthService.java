package com.ebook.bookforest.domain.auth.service;

import com.ebook.bookforest.domain.auth.dto.AuthUserResponse;
import com.ebook.bookforest.domain.auth.dto.LoginSession;
import com.ebook.bookforest.domain.auth.dto.ReissueResult;
import com.ebook.bookforest.domain.auth.exception.AuthErrorCode;
import com.ebook.bookforest.domain.auth.exception.AuthException;
import com.ebook.bookforest.domain.auth.model.RefreshTokenSession;
import com.ebook.bookforest.domain.auth.model.RefreshTokenRotationCommand;
import com.ebook.bookforest.domain.auth.model.RefreshTokenRotationStatus;
import com.ebook.bookforest.domain.auth.repository.RefreshTokenStore;
import com.ebook.bookforest.domain.user.entity.User;
import com.ebook.bookforest.domain.user.repository.UserRepository;
import com.ebook.bookforest.global.security.jwt.RefreshTokenHasher;
import com.ebook.bookforest.global.security.jwt.TokenProvider;
import com.ebook.bookforest.global.security.jwt.model.IssuedAccessToken;
import com.ebook.bookforest.global.security.jwt.model.IssuedRefreshToken;
import com.ebook.bookforest.global.security.jwt.model.ParsedRefreshToken;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration REFRESH_TOKEN_GRACE_PERIOD = Duration.ofSeconds(20);

    private final TokenProvider tokenProvider;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenStore refreshTokenStore;
    private final UserRepository userRepository;

    /**
     * 최초 로그인 세션 발급
     * @param userId
     * @return
     */
    public LoginSession createLoginSession(Long userId) {
        // 활성 사용자 확인
        User user = findActiveUser(userId);

        // RT 생성
        IssuedRefreshToken refreshToken = tokenProvider.createRefreshToken(user.getId());

        // RT 해시 생성
        String tokenHash = refreshTokenHasher.hash(refreshToken.value());

        // Redis 저장
        RefreshTokenSession session = RefreshTokenSession.initial(
            refreshToken.sessionId(),
            user.getId(),
            refreshToken.tokenId(),
            tokenHash,
            refreshToken.expiresAt()
        );

        refreshTokenStore.save(session);

        return new LoginSession(refreshToken.value(), refreshToken.expiresAt());
    }

    /**
     * RT 재발급
     * @param rawRefreshToken
     * @return
     */
    public ReissueResult reissue(String rawRefreshToken) {
        ParsedRefreshToken parsed = parseRefreshToken(rawRefreshToken);

        User user = findActiveUserOrRevoke(parsed.userId());

        IssuedAccessToken accessToken = tokenProvider.createAccessToken(
            user.getId(),
            user.getRole()
        );

        IssuedRefreshToken newRefreshToken = tokenProvider.rotateRefreshToken(
            user.getId(),
            parsed.sessionId()
        );

        String presentedHash = refreshTokenHasher.hash(rawRefreshToken);

        String newTokenHash = refreshTokenHasher.hash(newRefreshToken.value());

        Instant graceUntil = Instant.now().plus(REFRESH_TOKEN_GRACE_PERIOD);

        RefreshTokenRotationStatus status = refreshTokenStore.rotate(
            new RefreshTokenRotationCommand(
                user.getId(),
                parsed.sessionId(),
                parsed.tokenId(),
                presentedHash,
                newRefreshToken.tokenId(),
                newTokenHash,
                graceUntil,
                newRefreshToken.expiresAt()
            )
        );

        handleRotationStatus(status, user.getId());

        return new ReissueResult(
            accessToken.value(),
            tokenProvider.getAccessTokenExpiresInSeconds(),
            newRefreshToken.value(),
            newRefreshToken.expiresAt(),
            AuthUserResponse.from(user)
        );
    }

    /**
     * 로그아웃 처리
     * @param rawRefreshToken
     */
    public void logout(String rawRefreshToken) {
        if(rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        ParsedRefreshToken parsed;

        try{
            parsed = tokenProvider.parseRefreshToken(rawRefreshToken);
        } catch(JwtException | IllegalArgumentException e) {
            return;
        }

        refreshTokenStore.delete(parsed.userId(), parsed.sessionId());
    }



    /**
     * RT 파싱
     * @param rawRefreshToken
     * @return
     */
    private ParsedRefreshToken parseRefreshToken(
        String rawRefreshToken
    ) {
        try {
            return tokenProvider.parseRefreshToken(
                rawRefreshToken
            );
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN, e);
        }
    }

    /**
     * 활성 사용자 조회
     * @param userId
     * @return
     */
    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndActiveTrue(userId)
            .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_ACTIVE));
    }

    /**
     * 비활성 사용자 RT 제거
     * @param userId
     * @return
     */
    private User findActiveUserOrRevoke(Long userId) {
        try {
            return findActiveUser(userId);
        } catch (AuthException exception) {
            refreshTokenStore.deleteAllByUserId(userId);
            throw exception;
        }
    }

    /**
     * Redis Lua script 결과 처리
     * ROTATED : 정상 교체
     * NOT_FOUND : 토큰 없음
     * REUSE_DETECTED : 토큰 재사용
     * @param status
     * @param userId
     */
    private void handleRotationStatus(
        RefreshTokenRotationStatus status,
        Long userId
    ) {
        switch (status) {
            case ROTATED -> {} // 정상적으로 교체됐으므로 추가 작업 없음
            case NOT_FOUND -> throw new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
            case REUSE_DETECTED -> {
                refreshTokenStore.deleteAllByUserId(userId);
                throw new AuthException(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
            }
        }
    }


}
