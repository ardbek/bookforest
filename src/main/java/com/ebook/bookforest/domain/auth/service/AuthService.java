package com.ebook.bookforest.domain.auth.service;

import com.ebook.bookforest.domain.auth.dto.AuthUserResponse;
import com.ebook.bookforest.domain.auth.dto.LoginSession;
import com.ebook.bookforest.domain.auth.dto.ReissueResult;
import com.ebook.bookforest.domain.auth.exception.AuthErrorCode;
import com.ebook.bookforest.domain.auth.exception.AuthException;
import com.ebook.bookforest.domain.auth.model.RefreshTokenRotationCommand;
import com.ebook.bookforest.domain.auth.model.RefreshTokenRotationStatus;
import com.ebook.bookforest.domain.auth.model.RefreshTokenSession;
import com.ebook.bookforest.domain.auth.repository.RefreshTokenStore;
import com.ebook.bookforest.domain.user.dto.UserInfo;
import com.ebook.bookforest.domain.user.service.UserService;
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

/**
 * 로그인 세션 발급, Refresh Token 재발급과 로그아웃 서비스
 * Refresh Token 원문은 저장하지 않고 해시만 저장소에 보관
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 여러 탭이 동시에 재발급할 때 직전 Refresh Token을 허용하는 유예 시간 */
    private static final Duration REFRESH_TOKEN_GRACE_PERIOD = Duration.ofSeconds(20);

    private final TokenProvider tokenProvider;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenStore refreshTokenStore;
    private final UserService userService;

    /**
     * 로그인 세션 발급(소셜 로그인 성공 직후)
     *
     * @throws AuthException 활성 사용자가 아닌 경우 USER_NOT_ACTIVE
     */
    public LoginSession createLoginSession(Long userId) {
        UserInfo user = getActiveUser(userId);

        IssuedRefreshToken refreshToken = tokenProvider.createRefreshToken(user.id());
        RefreshTokenSession session = RefreshTokenSession.initial(
            refreshToken.sessionId(),
            user.id(),
            refreshToken.tokenId(),
            refreshTokenHasher.hash(refreshToken.value()),
            refreshToken.expiresAt()
        );
        refreshTokenStore.save(session);

        return new LoginSession(refreshToken.value(), refreshToken.expiresAt());
    }

    /**
     * Access Token과 Refresh Token 재발급
     * 비활성 사용자이거나 Refresh Token 재사용이 감지되면 사용자의 모든 세션 삭제
     *
     * @throws AuthException 토큰이 올바르지 않거나 세션이 없거나 재사용된 경우, 활성 사용자가 아닌 경우
     */
    public ReissueResult reissue(String rawRefreshToken) {
        ParsedRefreshToken parsed = parseRefreshToken(rawRefreshToken);
        UserInfo user = getActiveUserOrRevokeSessions(parsed.userId());

        IssuedAccessToken accessToken = tokenProvider.createAccessToken(user.id(), user.role());
        IssuedRefreshToken newRefreshToken = tokenProvider.rotateRefreshToken(user.id(), parsed.sessionId());

        RefreshTokenRotationStatus status = refreshTokenStore.rotate(new RefreshTokenRotationCommand(
            user.id(),
            parsed.sessionId(),
            parsed.tokenId(),
            refreshTokenHasher.hash(rawRefreshToken),
            newRefreshToken.tokenId(),
            refreshTokenHasher.hash(newRefreshToken.value()),
            Instant.now().plus(REFRESH_TOKEN_GRACE_PERIOD),
            newRefreshToken.expiresAt()
        ));
        handleRotationStatus(status, user.id());

        return new ReissueResult(
            accessToken.value(),
            tokenProvider.getAccessTokenExpiresInSeconds(),
            newRefreshToken.value(),
            newRefreshToken.expiresAt(),
            AuthUserResponse.from(user)
        );
    }

    /**
     * 로그아웃 처리(해당 세션만 삭제)
     * 로그아웃은 실패하지 않아야 하므로 비어 있거나 해석할 수 없는 토큰은 무시
     */
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        ParsedRefreshToken parsed;
        try {
            parsed = tokenProvider.parseRefreshToken(rawRefreshToken);
        } catch (JwtException | IllegalArgumentException exception) {
            return;
        }

        refreshTokenStore.delete(parsed.userId(), parsed.sessionId());
    }

    private ParsedRefreshToken parseRefreshToken(String rawRefreshToken) {
        try {
            return tokenProvider.parseRefreshToken(rawRefreshToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN, exception);
        }
    }

    private UserInfo getActiveUser(Long userId) {
        return userService.findActiveUserInfo(userId)
            .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_ACTIVE));
    }

    /** 탈퇴했거나 비활성화된 사용자의 남은 세션이 다시 쓰이지 않도록 전체 삭제 */
    private UserInfo getActiveUserOrRevokeSessions(Long userId) {
        try {
            return getActiveUser(userId);
        } catch (AuthException exception) {
            refreshTokenStore.deleteAllByUserId(userId);
            throw exception;
        }
    }

    /** 재사용 감지 시 탈취 가능성이 있어 사용자의 모든 세션 삭제 */
    private void handleRotationStatus(RefreshTokenRotationStatus status, Long userId) {
        switch (status) {
            case ROTATED -> {
            }
            case NOT_FOUND -> throw new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
            case REUSE_DETECTED -> {
                refreshTokenStore.deleteAllByUserId(userId);
                throw new AuthException(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
            }
        }
    }
}
