package com.ebook.bookforest.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        when(taskRepository
            .findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                eq(SocialUnlinkStatus.PROCESSING),
                any(LocalDateTime.class)
            ))
            .thenReturn(List.of(staleTask));
        when(taskRepository
            .findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                anyCollection(),
                any(LocalDateTime.class)
            ))
            .thenReturn(List.of(readyTask));

        scheduler.processTasks();

        ArgumentCaptor<LocalDateTime> staleBefore =
            ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> now =
            ArgumentCaptor.forClass(LocalDateTime.class);

        verify(taskRepository)
            .findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                eq(SocialUnlinkStatus.PROCESSING),
                staleBefore.capture()
            );
        verify(taskRepository)
            .findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                anyCollection(),
                now.capture()
            );
        assertEquals(now.getValue().minusMinutes(10), staleBefore.getValue());
        verify(stateService).recoverStaleProcessing(1L, now.getValue());
        verify(processor).process(2L);
    }

    private SocialUnlinkTask task(
        Long id,
        SocialUnlinkStatus status
    ) {
        return SocialUnlinkTask.builder()
            .id(id)
            .socialProvider(SocialProvider.KAKAO)
            .providerSubject("provider-user-" + id)
            .status(status)
            .retryCount(0)
            .build();
    }
}
