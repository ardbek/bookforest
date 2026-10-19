package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.support.SocialUnlinkTaskFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.ebook.bookforest.domain.user.entity.SocialUnlinkTask;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.enums.SocialUnlinkStatus;
import com.ebook.bookforest.domain.user.repository.SocialUnlinkTaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocialUnlinkRetrySchedulerTest {

    @Mock
    private SocialUnlinkTaskRepository taskRepository;

    @Mock
    private SocialUnlinkTaskStateService stateService;

    @Mock
    private SocialUnlinkTaskProcessor processor;

    @InjectMocks
    private SocialUnlinkRetryScheduler scheduler;

    @Test
    void 오래된_처리중_작업을_복구한_뒤_준비된_작업을_처리한다() {
        SocialUnlinkTask staleTask = task(1L, SocialUnlinkStatus.PROCESSING);
        SocialUnlinkTask readyTask = task(2L, SocialUnlinkStatus.PENDING);

        given(taskRepository
            .findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                eq(SocialUnlinkStatus.PROCESSING),
                any(LocalDateTime.class)
            ))
            .willReturn(List.of(staleTask));
        given(taskRepository
            .findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                anyCollection(),
                any(LocalDateTime.class)
            ))
            .willReturn(List.of(readyTask));

        scheduler.processTasks();

        ArgumentCaptor<LocalDateTime> staleBefore =
            ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> now =
            ArgumentCaptor.forClass(LocalDateTime.class);

        then(taskRepository).should()
            .findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                eq(SocialUnlinkStatus.PROCESSING),
                staleBefore.capture()
            );
        then(taskRepository).should()
            .findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                anyCollection(),
                now.capture()
            );
        assertThat(staleBefore.getValue()).isEqualTo(now.getValue().minusMinutes(10));
        then(stateService).should().recoverStaleProcessing(1L, now.getValue());
        then(processor).should().process(2L);
    }

    private SocialUnlinkTask task(
        Long id,
        SocialUnlinkStatus status
    ) {
        return SocialUnlinkTaskFixture.task(
            id, SocialProvider.KAKAO, "provider-user-" + id, null, null, status, 0
        );
    }
}
