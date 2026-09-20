package com.ebook.bookforest.global.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.global.security.jwt.model.IssuedAccessToken;
import com.ebook.bookforest.global.security.jwt.model.IssuedRefreshToken;
import com.ebook.bookforest.global.security.jwt.model.ParsedAccessToken;
import com.ebook.bookforest.global.security.jwt.model.ParsedRefreshToken;
import io.jsonwebtoken.MalformedJwtException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenProviderTest {

    private TokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901"
                .getBytes(StandardCharsets.UTF_8)
        );

        JwtProperties properties = new JwtProperties(
            secret,
            Duration.ofMinutes(30),
            Duration.ofDays(14)
        );

        tokenProvider = new TokenProvider(properties);
    }

    @Test
    void accessToken을_발급하고_파싱한다() {
        IssuedAccessToken issued =
            tokenProvider.createAccessToken(
                42L,
                UserRole.ROLE_USER
            );

        ParsedAccessToken parsed =
            tokenProvider.parseAccessToken(issued.value());

        assertEquals(42L, parsed.userId());
        assertEquals(issued.tokenId(), parsed.tokenId());
        assertEquals(UserRole.ROLE_USER, parsed.role());
    }

    @Test
    void refreshToken을_rotation해도_sessionId는_유지한다() {
        IssuedRefreshToken initial =
            tokenProvider.createRefreshToken(42L);

        IssuedRefreshToken rotated =
            tokenProvider.rotateRefreshToken(
                42L,
                initial.sessionId()
            );

        ParsedRefreshToken parsed =
            tokenProvider.parseRefreshToken(rotated.value());

        assertNotNull(initial.sessionId());
        assertEquals(initial.sessionId(), rotated.sessionId());
        assertEquals(rotated.sessionId(), parsed.sessionId());
        assertNotEquals(initial.tokenId(), rotated.tokenId());
    }

    @Test
    void refreshToken을_accessToken으로_파싱할_수_없다() {
        IssuedRefreshToken refreshToken =
            tokenProvider.createRefreshToken(42L);

        assertThrows(
            MalformedJwtException.class,
            () -> tokenProvider.parseAccessToken(
                refreshToken.value()
            )
        );
    }
}
