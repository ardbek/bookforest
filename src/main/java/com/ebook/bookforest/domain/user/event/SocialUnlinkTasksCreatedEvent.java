package com.ebook.bookforest.domain.user.event;

import java.util.List;

public record SocialUnlinkTasksCreatedEvent(List<Long> taskIds) {

    public SocialUnlinkTasksCreatedEvent {
        taskIds = List.copyOf(taskIds);
    }
}
