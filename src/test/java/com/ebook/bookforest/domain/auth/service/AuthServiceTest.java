package com.ebook.bookforest.domain.auth.service;

import static com.ebook.bookforest.support.EntityIds.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
import com.ebook.bookforest.domain.user.service.UserService;
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

    private AuthService authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = withId(User.create("책벌레", null, "https://example.com/profile.png"), USER_ID);
        authService = new AuthService(
            tokenProvider,
            refreshTokenHasher,
            refreshTokenStore,
            new UserService(userRepository)
        );
    }

    @Test
    void 로그인_세션을_만들면_Refresh_Token의_해시만_Redis에_저장한다() {
        IssuedRefreshToken refreshToken = new IssuedRefreshToken(
            RAW_REFRESH_TOKEN,
            OLD_TOKEN_ID,
            SESSION_ID,
            REFRESH_EXPIRES_AT
        );

        given(userRepository.findByIdAndActiveTrue(USER_ID))
            .willReturn(Optional.of(activeUser));
        given(tokenProvider.createRefreshToken(USER_ID))
            .willReturn(refreshToken);
        given(refreshTokenHasher.hash(RAW_REFRESH_TOKEN))
            .willReturn(PRESENTED_TOKEN_HASH);

        LoginSession result = authService.createLoginSession(USER_ID);

        assertThat(result.refreshToken()).isEqualTo(RAW_REFRESH_TOKEN);
        assertThat(result.refreshTokenExpiresAt()).isEqualTo(REFRESH_EXPIRES_AT);

        ArgumentCaptor<RefreshTokenSession> sessionCaptor =
            ArgumentCaptor.forClass(RefreshTokenSession.class);
        then(refreshTokenStore).should().save(sessionCaptor.capture());

        RefreshTokenSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.userId()).isEqualTo(USER_ID);
        assertThat(savedSession.sessionId()).isEqualTo(SESSION_ID);
        assertThat(savedSession.currentTokenId()).isEqualTo(OLD_TOKEN_ID);
        assertThat(savedSession.currentTokenHash()).isEqualTo(PRESENTED_TOKEN_HASH);
        assertThat(savedSession.currentTokenHash()).isNotEqualTo(RAW_REFRESH_TOKEN);
        assertThat(savedSession.previousTokenId()).isNull();
        assertThat(savedSession.previousTokenHash()).isNull();
        assertThat(savedSession.previousValidUntil()).isNull();
        assertThat(savedSession.expiresAt()).isEqualTo(REFRESH_EXPIRES_AT);
    }

    @Test
    void 비활성_사용자는_로그인_세션을_만들_수_없다() {
        given(userRepository.findByIdAndActiveTrue(USER_ID))
            .willReturn(Optional.empty());

        AuthException exception = assertThatExceptionOfType(AuthException.class)
            .isThrownBy(() -> authService.createLoginSession(USER_ID))
            .actual();

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_NOT_ACTIVE);
        then(tokenProvider).shouldHaveNoInteractions();
        then(refreshTokenHasher).shouldHaveNoInteractions();
        then(refreshTokenStore).shouldHaveNoInteractions();
    }

    @Test
    void Refresh_Token_rotation이_성공하면_새_토큰과_사용자_정보를_반환한다() {
        stubReissue(RefreshTokenRotationStatus.ROTATED);
        given(tokenProvider.getAccessTokenExpiresInSeconds())
            .willReturn(1800L);

        Instant beforeReissue = Instant.now();
        ReissueResult result = authService.reissue(RAW_REFRESH_TOKEN);

        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.accessTokenExpiresIn()).isEqualTo(1800L);
        assertThat(result.refreshToken()).isEqualTo(NEW_REFRESH_TOKEN);
        assertThat(result.refreshTokenExpiresAt()).isEqualTo(REFRESH_EXPIRES_AT);
        assertThat(result.user().userId()).isEqualTo(USER_ID);
        assertThat(result.user().displayName()).isEqualTo(activeUser.getDisplayName());
        assertThat(result.user().profileImageUrl()).isEqualTo(activeUser.getProfileImageUrl());

        ArgumentCaptor<RefreshTokenRotationCommand> commandCaptor =
            ArgumentCaptor.forClass(RefreshTokenRotationCommand.class);
        then(refreshTokenStore).should().rotate(commandCaptor.capture());

        RefreshTokenRotationCommand command = commandCaptor.getValue();
        assertThat(command.userId()).isEqualTo(USER_ID);
        assertThat(command.sessionId()).isEqualTo(SESSION_ID);
        assertThat(command.presentedTokenId()).isEqualTo(OLD_TOKEN_ID);
        assertThat(command.presentedTokenHash()).isEqualTo(PRESENTED_TOKEN_HASH);
        assertThat(command.newTokenId()).isEqualTo(NEW_TOKEN_ID);
        assertThat(command.newTokenHash()).isEqualTo(NEW_TOKEN_HASH);
        assertThat(command.newExpiresAt()).isEqualTo(REFRESH_EXPIRES_AT);
        assertThat(command.previousValidUntil()).isAfter(beforeReissue);
        then(refreshTokenStore).should(never()).deleteAllByUserId(USER_ID);
    }

    @Test
    void 잘못된_Refresh_Token으로_reissue하면_인증_오류가_발생한다() {
        given(tokenProvider.parseRefreshToken(RAW_REFRESH_TOKEN))
            .willThrow(new MalformedJwtException("invalid token"));

        AuthException exception = assertThatExceptionOfType(AuthException.class)
            .isThrownBy(() -> authService.reissue(RAW_REFRESH_TOKEN))
            .actual();

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
        then(refreshTokenStore).shouldHaveNoInteractions();
        then(userRepository).shouldHaveNoInteractions();
    }

    @Test
    void Redis에_세션이_없으면_Refresh_Token_NOT_FOUND_오류가_발생한다() {
        stubReissue(RefreshTokenRotationStatus.NOT_FOUND);

        AuthException exception = assertThatExceptionOfType(AuthException.class)
            .isThrownBy(() -> authService.reissue(RAW_REFRESH_TOKEN))
            .actual();

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
        then(refreshTokenStore).should(never()).deleteAllByUserId(USER_ID);
    }

    @Test
    void Refresh_Token_재사용이_탐지되면_사용자의_모든_세션을_삭제한다() {
        stubReissue(RefreshTokenRotationStatus.REUSE_DETECTED);

        AuthException exception = assertThatExceptionOfType(AuthException.class)
            .isThrownBy(() -> authService.reissue(RAW_REFRESH_TOKEN))
            .actual();

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        then(refreshTokenStore).should().deleteAllByUserId(USER_ID);
    }

    @Test
    void reissue할_사용자가_비활성이면_모든_세션을_삭제한다() {
        ParsedRefreshToken parsedToken = parsedRefreshToken();

        given(tokenProvider.parseRefreshToken(RAW_REFRESH_TOKEN))
            .willReturn(parsedToken);
        given(userRepository.findByIdAndActiveTrue(USER_ID))
            .willReturn(Optional.empty());

        AuthException exception = assertThatExceptionOfType(AuthException.class)
            .isThrownBy(() -> authService.reissue(RAW_REFRESH_TOKEN))
            .actual();

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_NOT_ACTIVE);
        then(refreshTokenStore).should().deleteAllByUserId(USER_ID);
        then(refreshTokenStore).should(never()).rotate(any());
    }

    @Test
    void 유효한_Refresh_Token으로_로그아웃하면_해당_세션을_삭제한다() {
        given(tokenProvider.parseRefreshToken(RAW_REFRESH_TOKEN))
            .willReturn(parsedRefreshToken());

        authService.logout(RAW_REFRESH_TOKEN);

        then(refreshTokenStore).should().delete(USER_ID, SESSION_ID);
    }

    @Test
    void 잘못된_Refresh_Token으로_로그아웃해도_예외가_발생하지_않는다() {
        given(tokenProvider.parseRefreshToken(RAW_REFRESH_TOKEN))
            .willThrow(new MalformedJwtException("invalid token"));

        authService.logout(RAW_REFRESH_TOKEN);

        then(refreshTokenStore).shouldHaveNoInteractions();
    }

    private void stubReissue(RefreshTokenRotationStatus rotationStatus) {
        given(tokenProvider.parseRefreshToken(RAW_REFRESH_TOKEN))
            .willReturn(parsedRefreshToken());
        given(userRepository.findByIdAndActiveTrue(USER_ID))
            .willReturn(Optional.of(activeUser));
        given(tokenProvider.createAccessToken(USER_ID, UserRole.ROLE_USER))
            .willReturn(new IssuedAccessToken(
                ACCESS_TOKEN,
                "access-token-id",
                ACCESS_EXPIRES_AT
            ));
        given(tokenProvider.rotateRefreshToken(USER_ID, SESSION_ID))
            .willReturn(new IssuedRefreshToken(
                NEW_REFRESH_TOKEN,
                NEW_TOKEN_ID,
                SESSION_ID,
                REFRESH_EXPIRES_AT
            ));
        given(refreshTokenHasher.hash(RAW_REFRESH_TOKEN))
            .willReturn(PRESENTED_TOKEN_HASH);
        given(refreshTokenHasher.hash(NEW_REFRESH_TOKEN))
            .willReturn(NEW_TOKEN_HASH);
        given(refreshTokenStore.rotate(any(RefreshTokenRotationCommand.class)))
            .willReturn(rotationStatus);
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
