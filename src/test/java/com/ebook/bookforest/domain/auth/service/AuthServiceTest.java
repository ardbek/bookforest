package com.ebook.bookforest.domain.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ebook.bookforest.domain.auth.dto.LoginSession;
import com.ebook.bookforest.domain.auth.dto.ReissueResult;
import com.ebook.bookforest.domain.auth.exception.AuthErrorCode;
import com.ebook.bookforest.domain.auth.exception.AuthException;
import com.ebook.bookforest.domain.auth.model.RefreshTokenRotationCommand;
import com.ebook.bookforest.domain.auth.model.RefreshTokenRotationStatus;
import com.ebook.bookforest.domain.auth.model.RefreshTokenSession;
import com.ebook.bookforest.domain.auth.repository.RefreshTokenStore;
import com.ebook.bookforest.domain.user.entity.User;
import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.domain.user.repository.UserRepository;
import com.ebook.bookforest.global.security.jwt.RefreshTokenHasher;
import com.ebook.bookforest.global.security.jwt.TokenProvider;
import com.ebook.bookforest.global.security.jwt.model.IssuedAccessToken;
import com.ebook.bookforest.global.security.jwt.model.IssuedRefreshToken;
import com.ebook.bookforest.global.security.jwt.model.ParsedRefreshToken;
import io.jsonwebtoken.MalformedJwtException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Long USER_ID = 1L;
    private static final String SESSION_ID = "session-id";
    private static final String OLD_TOKEN_ID = "old-token-id";
    private static final String NEW_TOKEN_ID = "new-token-id";
    private static final String RAW_REFRESH_TOKEN = "raw-refresh-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String PRESENTED_TOKEN_HASH = "presented-token-hash";
    private static final String NEW_TOKEN_HASH = "new-token-hash";
    private static final Instant REFRESH_EXPIRES_AT =
        Instant.parse("2099-01-01T00:00:00Z");
    private static final Instant ACCESS_EXPIRES_AT =
        Instant.parse("2098-12-31T00:30:00Z");

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthService authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
            .id(USER_ID)
            .displayName("책벌레")
            .profileImageUrl("https://example.com/profile.png")
            .role(UserRole.ROLE_USER)
            .active(true)
            .build();
    }

    @Test
    void 로그인_세션을_만들면_Refresh_Token의_해시만_Redis에_저장한다() {
        IssuedRefreshToken refreshToken = new IssuedRefreshToken(
            RAW_REFRESH_TOKEN,
            OLD_TOKEN_ID,
            SESSION_ID,
            REFRESH_EXPIRES_AT
        );

        when(userRepository.findByIdAndActiveTrue(USER_ID))
            .thenReturn(Optional.of(activeUser));
        when(tokenProvider.createRefreshToken(USER_ID))
            .thenReturn(refreshToken);
        when(refreshTokenHasher.hash(RAW_REFRESH_TOKEN))
            .thenReturn(PRESENTED_TOKEN_HASH);

        LoginSession result = authService.createLoginSession(USER_ID);

        assertEquals(RAW_REFRESH_TOKEN, result.refreshToken());
        assertEquals(REFRESH_EXPIRES_AT, result.refreshTokenExpiresAt());

        ArgumentCaptor<RefreshTokenSession> sessionCaptor =
            ArgumentCaptor.forClass(RefreshTokenSession.class);
        verify(refreshTokenStore).save(sessionCaptor.capture());

        RefreshTokenSession savedSession = sessionCaptor.getValue();
        assertEquals(USER_ID, savedSession.userId());
        assertEquals(SESSION_ID, savedSession.sessionId());
        assertEquals(OLD_TOKEN_ID, savedSession.currentTokenId());
        assertEquals(PRESENTED_TOKEN_HASH, savedSession.currentTokenHash());
        assertFalse(RAW_REFRESH_TOKEN.equals(savedSession.currentTokenHash()));
        assertNull(savedSession.previousTokenId());
        assertNull(savedSession.previousTokenHash());
        assertNull(savedSession.previousValidUntil());
        assertEquals(REFRESH_EXPIRES_AT, savedSession.expiresAt());
    }

    @Test
    void 비활성_사용자는_로그인_세션을_만들_수_없다() {
        when(userRepository.findByIdAndActiveTrue(USER_ID))
            .thenReturn(Optional.empty());

        AuthException exception = assertThrows(
            AuthException.class,
            () -> authService.createLoginSession(USER_ID)
        );

        assertEquals(AuthErrorCode.USER_NOT_ACTIVE, exception.getErrorCode());
        verifyNoInteractions(tokenProvider, refreshTokenHasher, refreshTokenStore);
    }

    @Test
    void Refresh_Token_rotation이_성공하면_새_토큰과_사용자_정보를_반환한다() {
        stubReissue(RefreshTokenRotationStatus.ROTATED);
        when(tokenProvider.getAccessTokenExpiresInSeconds())
            .thenReturn(1800L);

        Instant beforeReissue = Instant.now();
        ReissueResult result = authService.reissue(RAW_REFRESH_TOKEN);

        assertEquals(ACCESS_TOKEN, result.accessToken());
        assertEquals(1800L, result.accessTokenExpiresIn());
        assertEquals(NEW_REFRESH_TOKEN, result.refreshToken());
        assertEquals(REFRESH_EXPIRES_AT, result.refreshTokenExpiresAt());
        assertEquals(USER_ID, result.user().userId());
        assertEquals(activeUser.getDisplayName(), result.user().displayName());
        assertEquals(activeUser.getProfileImageUrl(), result.user().profileImageUrl());

        ArgumentCaptor<RefreshTokenRotationCommand> commandCaptor =
            ArgumentCaptor.forClass(RefreshTokenRotationCommand.class);
        verify(refreshTokenStore).rotate(commandCaptor.capture());

        RefreshTokenRotationCommand command = commandCaptor.getValue();
        assertEquals(USER_ID, command.userId());
        assertEquals(SESSION_ID, command.sessionId());
        assertEquals(OLD_TOKEN_ID, command.presentedTokenId());
        assertEquals(PRESENTED_TOKEN_HASH, command.presentedTokenHash());
        assertEquals(NEW_TOKEN_ID, command.newTokenId());
        assertEquals(NEW_TOKEN_HASH, command.newTokenHash());
        assertEquals(REFRESH_EXPIRES_AT, command.newExpiresAt());
        assertTrue(command.previousValidUntil().isAfter(beforeReissue));
        verify(refreshTokenStore, never()).deleteAllByUserId(USER_ID);
    }

    @Test
    void 잘못된_Refresh_Token으로_reissue하면_인증_오류가_발생한다() {
        when(tokenProvider.parseRefreshToken(RAW_REFRESH_TOKEN))
            .thenThrow(new MalformedJwtException("invalid token"));

        AuthException exception = assertThrows(
            AuthException.class,
            () -> authService.reissue(RAW_REFRESH_TOKEN)
        );

        assertEquals(
            AuthErrorCode.INVALID_REFRESH_TOKEN,
            exception.getErrorCode()
        );
        verifyNoInteractions(refreshTokenStore, userRepository);
    }

    @Test
    void Redis에_세션이_없으면_Refresh_Token_NOT_FOUND_오류가_발생한다() {
        stubReissue(RefreshTokenRotationStatus.NOT_FOUND);

        AuthException exception = assertThrows(
            AuthException.class,
            () -> authService.reissue(RAW_REFRESH_TOKEN)
        );

        assertEquals(
            AuthErrorCode.REFRESH_TOKEN_NOT_FOUND,
            exception.getErrorCode()
        );
        verify(refreshTokenStore, never()).deleteAllByUserId(USER_ID);
    }

    @Test
    void Refresh_Token_재사용이_탐지되면_사용자의_모든_세션을_삭제한다() {
        stubReissue(RefreshTokenRotationStatus.REUSE_DETECTED);

        AuthException exception = assertThrows(
            AuthException.class,
            () -> authService.reissue(RAW_REFRESH_TOKEN)
        );

        assertEquals(
            AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED,
            exception.getErrorCode()
        );
        verify(refreshTokenStore).deleteAllByUserId(USER_ID);
    }

    @Test
    void reissue할_사용자가_비활성이면_모든_세션을_삭제한다() {
        ParsedRefreshToken parsedToken = parsedRefreshToken();

        when(tokenProvider.parseRefreshToken(RAW_REFRESH_TOKEN))
            .thenReturn(parsedToken);
        when(userRepository.findByIdAndActiveTrue(USER_ID))
            .thenReturn(Optional.empty());

        AuthException exception = assertThrows(
            AuthException.class,
            () -> authService.reissue(RAW_REFRESH_TOKEN)
        );

        assertEquals(AuthErrorCode.USER_NOT_ACTIVE, exception.getErrorCode());
        verify(refreshTokenStore).deleteAllByUserId(USER_ID);
        verify(refreshTokenStore, never()).rotate(any());
    }

    @Test
    void 유효한_Refresh_Token으로_로그아웃하면_해당_세션을_삭제한다() {
        when(tokenProvider.parseRefreshToken(RAW_REFRESH_TOKEN))
            .thenReturn(parsedRefreshToken());

        authService.logout(RAW_REFRESH_TOKEN);

        verify(refreshTokenStore).delete(USER_ID, SESSION_ID);
    }

    @Test
    void 잘못된_Refresh_Token으로_로그아웃해도_예외가_발생하지_않는다() {
        when(tokenProvider.parseRefreshToken(RAW_REFRESH_TOKEN))
            .thenThrow(new MalformedJwtException("invalid token"));

        authService.logout(RAW_REFRESH_TOKEN);

        verifyNoInteractions(refreshTokenStore);
    }

    private void stubReissue(RefreshTokenRotationStatus rotationStatus) {
        when(tokenProvider.parseRefreshToken(RAW_REFRESH_TOKEN))
            .thenReturn(parsedRefreshToken());
        when(userRepository.findByIdAndActiveTrue(USER_ID))
            .thenReturn(Optional.of(activeUser));
        when(tokenProvider.createAccessToken(USER_ID, UserRole.ROLE_USER))
            .thenReturn(new IssuedAccessToken(
                ACCESS_TOKEN,
                "access-token-id",
                ACCESS_EXPIRES_AT
            ));
        when(tokenProvider.rotateRefreshToken(USER_ID, SESSION_ID))
            .thenReturn(new IssuedRefreshToken(
                NEW_REFRESH_TOKEN,
                NEW_TOKEN_ID,
                SESSION_ID,
                REFRESH_EXPIRES_AT
            ));
        when(refreshTokenHasher.hash(RAW_REFRESH_TOKEN))
            .thenReturn(PRESENTED_TOKEN_HASH);
        when(refreshTokenHasher.hash(NEW_REFRESH_TOKEN))
            .thenReturn(NEW_TOKEN_HASH);
        when(refreshTokenStore.rotate(any(RefreshTokenRotationCommand.class)))
            .thenReturn(rotationStatus);
    }

    private ParsedRefreshToken parsedRefreshToken() {
        return new ParsedRefreshToken(
            USER_ID,
            OLD_TOKEN_ID,
            SESSION_ID,
            REFRESH_EXPIRES_AT
        );
    }
}
