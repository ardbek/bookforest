package com.ebook.bookforest.global.security.jwt;

import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.global.security.jwt.model.IssuedAccessToken;
import com.ebook.bookforest.global.security.jwt.model.IssuedRefreshToken;
import com.ebook.bookforest.global.security.jwt.model.ParsedAccessToken;
import com.ebook.bookforest.global.security.jwt.model.ParsedRefreshToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Access Token, Refresh Token 발급과 검증
 * 두 토큰은 type 클레임으로 구분하고, Refresh Token은 로그인 세션 ID(sid)를 함께 담음
 */
@Component
public class TokenProvider {

    private static final String SESSION_ID_CLAIM = "sid";
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public TokenProvider(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.accessTokenTtl = properties.accessTokenTtl();
        this.refreshTokenTtl = properties.refreshTokenTtl();
    }

    /** Access Token 발급 */
    public IssuedAccessToken createAccessToken(Long userId, UserRole role) {
        Objects.requireNonNull(userId, "userId는 null일 수 없습니다.");
        Objects.requireNonNull(role, "role은 null일 수 없습니다.");

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        String tokenId = UUID.randomUUID().toString();

        String token = Jwts.builder()
            .subject(String.valueOf(userId))
            .id(tokenId)
            .claim(TOKEN_TYPE_CLAIM, TokenType.ACCESS.name())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .claim(ROLE_CLAIM, role.name())
            .signWith(signingKey)
            .compact();

        return new IssuedAccessToken(token, tokenId, expiresAt);
    }

    /** Refresh Token 발급(새 로그인 세션) */
    public IssuedRefreshToken createRefreshToken(Long userId) {
        Objects.requireNonNull(userId, "userId는 null일 수 없습니다.");
        return createRefreshToken(userId, UUID.randomUUID().toString());
    }

    /** Refresh Token 재발급(기존 로그인 세션 유지) */
    public IssuedRefreshToken rotateRefreshToken(Long userId, String sessionId) {
        Objects.requireNonNull(userId, "userId는 null일 수 없습니다.");
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId는 비어 있을 수 없습니다.");
        }
        return createRefreshToken(userId, sessionId);
    }

    /**
     * Access Token 검증과 파싱
     *
     * @throws io.jsonwebtoken.JwtException 서명, 만료, 토큰 종류, 사용자 ID, 권한 값이 올바르지 않은 경우
     */
    public ParsedAccessToken parseAccessToken(String token) {
        Claims claims = parseClaims(token, TokenType.ACCESS);

        return new ParsedAccessToken(
            parseUserId(claims),
            claims.getId(),
            parseRole(claims),
            claims.getExpiration().toInstant()
        );
    }

    /**
     * Refresh Token 검증과 파싱
     *
     * @throws io.jsonwebtoken.JwtException 서명, 만료, 토큰 종류, 사용자 ID, 세션 ID 값이 올바르지 않은 경우
     */
    public ParsedRefreshToken parseRefreshToken(String token) {
        Claims claims = parseClaims(token, TokenType.REFRESH);

        String sessionId = claims.get(SESSION_ID_CLAIM, String.class);
        if (sessionId == null || sessionId.isBlank()) {
            throw new MalformedJwtException("Refresh Token 세션 식별값이 없습니다.");
        }

        return new ParsedRefreshToken(
            parseUserId(claims),
            claims.getId(),
            sessionId,
            claims.getExpiration().toInstant()
        );
    }

    public long getAccessTokenExpiresInSeconds() {
        return accessTokenTtl.getSeconds();
    }

    public long getRefreshTokenExpiresInSeconds() {
        return refreshTokenTtl.getSeconds();
    }

    private IssuedRefreshToken createRefreshToken(Long userId, String sessionId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(refreshTokenTtl);
        String tokenId = UUID.randomUUID().toString();

        String token = Jwts.builder()
            .subject(String.valueOf(userId))
            .id(tokenId)
            .claim(TOKEN_TYPE_CLAIM, TokenType.REFRESH.name())
            .claim(SESSION_ID_CLAIM, sessionId)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact();

        return new IssuedRefreshToken(token, tokenId, sessionId, expiresAt);
    }

    // 서명과 만료는 파서가 검사하고, Access Token과 Refresh Token을 바꿔 쓰지 못하도록 종류를 따로 검사
    private Claims parseClaims(String token, TokenType expectedType) {
        if (token == null || token.isBlank()) {
            throw new MalformedJwtException("JWT가 비어 있습니다.");
        }

        Claims claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
        if (!expectedType.name().equals(tokenType)) {
            throw new MalformedJwtException("JWT 타입이 올바르지 않습니다.");
        }

        return claims;
    }

    private Long parseUserId(Claims claims) {
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new MalformedJwtException("JWT 사용자 식별값이 없습니다.");
        }

        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new MalformedJwtException("JWT 사용자 식별값이 올바르지 않습니다.", exception);
        }
    }

    private UserRole parseRole(Claims claims) {
        String roleValue = claims.get(ROLE_CLAIM, String.class);
        if (roleValue == null || roleValue.isBlank()) {
            throw new MalformedJwtException("Access Token 권한 정보가 없습니다.");
        }

        try {
            return UserRole.valueOf(roleValue);
        } catch (IllegalArgumentException exception) {
            throw new MalformedJwtException("Access Token 권한 정보가 올바르지 않습니다.", exception);
        }
    }

    private enum TokenType {
        ACCESS,
        REFRESH
    }
}
