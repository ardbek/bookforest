package com.ebook.bookforest.global.security.jwt.model;

import java.time.Instant;

public record IssuedAccessToken(
    String value,
    String tokenId,
    Instant expiresAt
) {
}