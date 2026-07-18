package com.ebook.bookforest.domain.user.exception;

public class SocialUnlinkInProgressException extends RuntimeException {

    public SocialUnlinkInProgressException() {
        super("소셜 계정 연동 해제가 진행 중입니다.");
    }
}
