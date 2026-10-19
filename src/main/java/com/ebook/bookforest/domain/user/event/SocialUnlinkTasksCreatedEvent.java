package com.ebook.bookforest.domain.user.event;

import java.util.List;

/**
 * 회원 탈퇴로 소셜 연동 해제 작업이 만들어졌다는 이벤트
 */
public record SocialUnlinkTasksCreatedEvent(List<Long> taskIds) {

    public SocialUnlinkTasksCreatedEvent {
        taskIds = List.copyOf(taskIds);
    }
}
