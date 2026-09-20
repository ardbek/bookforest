package com.ebook.bookforest.domain.user.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebook.bookforest.domain.user.enums.ProviderTokenType;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.enums.SocialUnlinkStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SocialUnlinkTaskTest {

    private static final LocalDateTime NOW =
        LocalDateTime.of(2026, 7, 18, 12, 0);

    @Test
    void 생성된_작업은_즉시_처리할_수_있다() {
        SocialUnlinkTask task = createTask();

        assertEquals(SocialUnlinkStatus.PENDING, task.getStatus());
        assertEquals(NOW, task.getNextRetryAt());
        assertTrue(task.isReady(NOW));
        assertFalse(task.isReady(NOW.minusNanos(1)));
    }

    @Test
    void 처리중인_작업을_성공으로_완료한다() {
        SocialUnlinkTask task = createTask();
        task.startProcessing();

        task.markSucceeded(NOW.plusMinutes(1));

        assertEquals(SocialUnlinkStatus.SUCCEEDED, task.getStatus());
        assertEquals(NOW.plusMinutes(1), task.getCompletedAt());
        assertNull(task.getNextRetryAt());
        assertNull(task.getProviderTokenCiphertext());
        assertNull(task.getProviderTokenType());
    }

    @Test
    void 일시적_실패는_재시도를_예약한다() {
        SocialUnlinkTask task = createTask();
        task.startProcessing();

        task.scheduleRetry("TEMPORARY", NOW.plusMinutes(1));

        assertEquals(SocialUnlinkStatus.RETRY_WAITING, task.getStatus());
        assertEquals(1, task.getRetryCount());
        assertEquals("TEMPORARY", task.getLastErrorCode());
        assertTrue(task.isReady(NOW.plusMinutes(1)));
    }

    @Test
    void 잘못된_재시도_입력은_상태를_변경하지_않는다() {
        SocialUnlinkTask task = createTask();
        task.startProcessing();

        assertThrows(
            NullPointerException.class,
            () -> task.scheduleRetry("TEMPORARY", null)
        );

        assertEquals(SocialUnlinkStatus.PROCESSING, task.getStatus());
        assertEquals(0, task.getRetryCount());
        assertNull(task.getLastErrorCode());
    }

    @Test
    void 성공한_작업은_다시_처리할_수_없다() {
        SocialUnlinkTask task = createTask();
        task.startProcessing();
        task.markSucceeded(NOW.plusMinutes(1));

        assertThrows(IllegalStateException.class, task::startProcessing);
    }

    private SocialUnlinkTask createTask() {
        return SocialUnlinkTask.createPending(
            SocialProvider.NAVER,
            "naver-user-id",
            "encrypted-token",
            ProviderTokenType.REFRESH_TOKEN,
            NOW
        );
    }
}
