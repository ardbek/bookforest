package com.ebook.bookforest.domain.user.event;

/**
 * 회원 탈퇴 완료 이벤트, 탈퇴 커밋 후 다른 도메인이 사용자 데이터를 정리할 때 사용
 */
public record UserWithdrawnEvent(Long userId) {
}
