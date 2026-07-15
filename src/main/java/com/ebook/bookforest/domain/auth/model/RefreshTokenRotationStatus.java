package com.ebook.bookforest.domain.auth.model;

public enum RefreshTokenRotationStatus {
    ROTATED,
    NOT_FOUND,
    REUSE_DETECTED
}