package com.ebook.bookforest.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.ebook.bookforest.domain.user.entity.SocialUnlinkTask;
import com.ebook.bookforest.domain.user.enums.ProviderTokenType;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.enums.SocialUnlinkStatus;
import com.ebook.bookforest.domain.user.repository.SocialUnlinkTaskRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocialUnlinkTaskStateServiceTest {

    private static final Long TASK_ID = 1L;
    private static final LocalDateTime NOW =
        LocalDateTime.of(2026, 7, 18, 12, 0);

    @Mock
    private SocialUnlinkTaskRepository taskRepository;

    @InjectMocks
    private SocialUnlinkTaskStateService service;

    private SocialUnlinkTask task;

    @BeforeEach
    void setUp() {
        task = SocialUnlinkTask.builder()
            .id(TASK_ID)
            .socialProvider(SocialProvider.NAVER)
            .providerSubject("naver-user-id")
            .providerTokenCiphertext("encrypted-token")
            .providerTokenType(ProviderTokenType.REFRESH_TOKEN)
            .status(SocialUnlinkStatus.PENDING)
            .retryCount(0)
            .nextRetryAt(NOW)
            .build();

        when(taskRepository.findByIdForUpdate(TASK_ID))
            .thenReturn(Optional.of(task));
    }

    @Test
    void 처리_가능한_작업을_선점한다() {
        Optional<SocialUnlinkWork> claimed = service.claim(TASK_ID, NOW);

        assertTrue(claimed.isPresent());
        assertEquals(TASK_ID, claimed.orElseThrow().taskId());
        assertEquals(SocialUnlinkStatus.PROCESSING, task.getStatus());
    }

    @Test
    void 재시도_시간이_되지_않은_작업은_선점하지_않는다() {
        Optional<SocialUnlinkWork> claimed = service.claim(
            TASK_ID,
            NOW.minusSeconds(1)
        );

        assertTrue(claimed.isEmpty());
        assertEquals(SocialUnlinkStatus.PENDING, task.getStatus());
    }

    @Test
    void 일시적_실패는_최대_다섯_번_재시도한_후_실패로_종료한다() {
        for (int retry = 1; retry <= 5; retry++) {
            task.startProcessing();
            LocalDateTime failedAt = NOW.plusHours(retry);

            service.handleFailure(
                TASK_ID,
                true,
                "TEMPORARY_ERROR",
                failedAt
            );

            assertEquals(
                SocialUnlinkStatus.RETRY_WAITING,
                task.getStatus()
            );
            assertEquals(retry, task.getRetryCount());
            assertEquals(
                failedAt.plusMinutes(1L << (retry - 1)),
                task.getNextRetryAt()
            );
        }

        task.startProcessing();
        service.handleFailure(
            TASK_ID,
            true,
            "TEMPORARY_ERROR",
            NOW.plusHours(6)
        );

        assertEquals(SocialUnlinkStatus.FAILED, task.getStatus());
        assertEquals(5, task.getRetryCount());
        assertEquals("TEMPORARY_ERROR", task.getLastErrorCode());
        assertFalse(task.isReady(NOW.plusDays(1)));
    }

    @Test
    void 영구_오류는_재시도하지_않고_즉시_실패한다() {
        task.startProcessing();

        service.handleFailure(
            TASK_ID,
            false,
            "INVALID_TOKEN",
            NOW
        );

        assertEquals(SocialUnlinkStatus.FAILED, task.getStatus());
        assertEquals(0, task.getRetryCount());
        assertEquals("INVALID_TOKEN", task.getLastErrorCode());
    }

    @Test
    void 제한_시간을_넘긴_처리중_작업은_재시도를_예약한다() {
        task.startProcessing();

        boolean recovered = service.recoverStaleProcessing(TASK_ID, NOW);

        assertTrue(recovered);
        assertEquals(SocialUnlinkStatus.RETRY_WAITING, task.getStatus());
        assertEquals(1, task.getRetryCount());
        assertEquals("PROCESSING_TIMEOUT", task.getLastErrorCode());
        assertEquals(NOW.plusMinutes(1), task.getNextRetryAt());
    }

    @Test
    void 처리중이_아닌_작업은_복구하지_않는다() {
        boolean recovered = service.recoverStaleProcessing(TASK_ID, NOW);

        assertFalse(recovered);
        assertEquals(SocialUnlinkStatus.PENDING, task.getStatus());
        assertEquals(0, task.getRetryCount());
    }

    @Test
    void 재시도_한도에_도달한_처리중_작업은_실패로_종료한다() {
        task = SocialUnlinkTask.builder()
            .id(TASK_ID)
            .socialProvider(SocialProvider.NAVER)
            .providerSubject("naver-user-id")
            .providerTokenCiphertext("encrypted-token")
            .providerTokenType(ProviderTokenType.REFRESH_TOKEN)
            .status(SocialUnlinkStatus.PROCESSING)
            .retryCount(5)
            .build();
        when(taskRepository.findByIdForUpdate(TASK_ID))
            .thenReturn(Optional.of(task));

        boolean recovered = service.recoverStaleProcessing(TASK_ID, NOW);

        assertTrue(recovered);
        assertEquals(SocialUnlinkStatus.FAILED, task.getStatus());
        assertEquals(5, task.getRetryCount());
        assertEquals("PROCESSING_TIMEOUT", task.getLastErrorCode());
        assertNull(task.getNextRetryAt());
    }
}
