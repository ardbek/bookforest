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

@Component
public class TokenProvider {

    private static final String SESSION_ID_CLAIM = "sid";
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public TokenProvider(JwtProperties properties) {
        byte[] keyBytes = Decoders.BASE64.decode(properties.secret());

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenTtl = properties.accessTokenTtl();
        this.refreshTokenTtl = properties.refreshTokenTtl();
    }

    /**
     * Access token 발급
     *
     * @param userId
     * @param role
     * @return
     */
    public IssuedAccessToken createAccessToken(Long userId, UserRole role) {

        Objects.requireNonNull(userId, "userId는 null일 수 없음.");
        Objects.requireNonNull(role, "role은 null일 수 없음.");

        return createToken(
            userId,
            TokenType.ACCESS,
            role,
            accessTokenTtl
        );
    }

    /**
     * Refresh token 발급
     * @param userId
     * @return
     */
    public IssuedRefreshToken createRefreshToken(Long userId) {
        Objects.requireNonNull(userId, "userId는 null일 수 없습니다.");

        String sessionId = UUID.randomUUID().toString();

        return createRefreshToken(userId, sessionId);
    }

    /**
     * 기존 로그인 세션을 유지하며 Refresh Token rotation
     * @param userId
     * @param sessionId
     * @return
     */
    public IssuedRefreshToken rotateRefreshToken(Long userId, String sessionId) {
        Objects.requireNonNull(userId, "userId는 null일 수 없습니다.");

        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId는 비어 있을 수 없습니다.");
        }

        return createRefreshToken(userId, sessionId);
    }

    public long getAccessTokenExpiresInSeconds() {
        return accessTokenTtl.getSeconds();
    }

    public long getRefreshTokenExpiresInSeconds() {
        return refreshTokenTtl.getSeconds();
    }

    private IssuedAccessToken createToken(
        Long userId,
        TokenType tokenType,
        UserRole userRole,
        Duration ttl
    ) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);
        String tokenId = UUID.randomUUID().toString();

        var builder = Jwts.builder()
            .subject(String.valueOf(userId))
            .id(tokenId)
            .claim(TOKEN_TYPE_CLAIM, tokenType.name())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt));

        if(userRole != null) {
            builder.claim(ROLE_CLAIM, userRole.name());
        }

        String token = builder.signWith(signingKey).compact();

        return new IssuedAccessToken(token, tokenId, expiresAt);
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

        return new IssuedRefreshToken(
            token,
            tokenId,
            sessionId,
            expiresAt
        );
    }

    public ParsedAccessToken parseAccessToken(String token) {
        Claims claims = parseClaims(token, TokenType.ACCESS);

        return new ParsedAccessToken(
            parseUserId(claims),
            claims.getId(),
            parseRole(claims),
            claims.getExpiration().toInstant()
        );
    }

    public ParsedRefreshToken parseRefreshToken(String token) {
        Claims claims = parseClaims(token, TokenType.REFRESH);

        String sessionId = claims.get(SESSION_ID_CLAIM, String.class);

        if (sessionId == null || sessionId.isBlank()) {
            throw new MalformedJwtException(
                "Refresh Token 세션 식별값이 없습니다."
            );
        }

        return new ParsedRefreshToken(
            parseUserId(claims),
            claims.getId(),
            sessionId,
            claims.getExpiration().toInstant()
        );
    }

    /**
     * JWT 검사
     * 1. 서명 검사
     * 2. 만료 시간 검사
     * 3. Access/Refresh 타입 검사
     * @param token
     * @param expectedType
     * @return Claims
     */
    private Claims parseClaims(
        String token,
        TokenType expectedType
    ) {
        if (token == null || token.isBlank()) {
            throw new MalformedJwtException("JWT가 비어 있습니다.");
        }

        Claims claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        String tokenType = claims.get(
            TOKEN_TYPE_CLAIM,
            String.class
        );

        if (!expectedType.name().equals(tokenType)) {
            throw new MalformedJwtException("JWT 타입이 올바르지 않습니다.");
        }

        return claims;
    }

    /**
     * JWT sub를 Long 타입 UserId로 변환
     * @param claims
     * @return
     */
    private Long parseUserId(Claims claims) {
        String subject = claims.getSubject();

        if (subject == null || subject.isBlank()) {
            throw new MalformedJwtException(
                "JWT 사용자 식별값이 없습니다."
            );
        }

        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new MalformedJwtException(
                "JWT 사용자 식별값이 올바르지 않습니다.",
                exception
            );
        }
    }

    /**
     * 문자열 권한을 enum으로 변환
     * ex: "ROLE_USER" -> UserRole.ROLE_USER
     * @param claims
     * @return
     */
    private UserRole parseRole(Claims claims) {
        String roleValue = claims.get(
            ROLE_CLAIM,
            String.class
        );

        if (roleValue == null || roleValue.isBlank()) {
            throw new MalformedJwtException(
                "Access Token 권한 정보가 없습니다."
            );
        }

        try {
            return UserRole.valueOf(roleValue);
        } catch (IllegalArgumentException exception) {
            throw new MalformedJwtException(
                "Access Token 권한 정보가 올바르지 않습니다.",
                exception
            );
        }
    }

    /**
     * 토큰 타입
     */
    private enum TokenType {
        ACCESS,
        REFRESH
    }

}
