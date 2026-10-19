package com.ebook.bookforest.domain.user.entity;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(task.getStatus()).isEqualTo(SocialUnlinkStatus.PENDING);
        assertThat(task.getNextRetryAt()).isEqualTo(NOW);
        assertThat(task.isReady(NOW)).isTrue();
        assertThat(task.isReady(NOW.minusNanos(1))).isFalse();
    }

    @Test
    void 처리중인_작업을_성공으로_완료한다() {
        SocialUnlinkTask task = createTask();
        task.startProcessing();

        task.markSucceeded(NOW.plusMinutes(1));

        assertThat(task.getStatus()).isEqualTo(SocialUnlinkStatus.SUCCEEDED);
        assertThat(task.getCompletedAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(task.getNextRetryAt()).isNull();
        assertThat(task.getProviderTokenCiphertext()).isNull();
        assertThat(task.getProviderTokenType()).isNull();
    }

    @Test
    void 일시적_실패는_재시도를_예약한다() {
        SocialUnlinkTask task = createTask();
        task.startProcessing();

        task.scheduleRetry("TEMPORARY", NOW.plusMinutes(1));

        assertThat(task.getStatus()).isEqualTo(SocialUnlinkStatus.RETRY_WAITING);
        assertThat(task.getRetryCount()).isEqualTo(1);
        assertThat(task.getLastErrorCode()).isEqualTo("TEMPORARY");
        assertThat(task.isReady(NOW.plusMinutes(1))).isTrue();
    }

    @Test
    void 잘못된_재시도_입력은_상태를_변경하지_않는다() {
        SocialUnlinkTask task = createTask();
        task.startProcessing();

        assertThatThrownBy(() -> task.scheduleRetry("TEMPORARY", null)).isInstanceOf(NullPointerException.class);

        assertThat(task.getStatus()).isEqualTo(SocialUnlinkStatus.PROCESSING);
        assertThat(task.getRetryCount()).isEqualTo(0);
        assertThat(task.getLastErrorCode()).isNull();
    }

    @Test
    void 성공한_작업은_다시_처리할_수_없다() {
        SocialUnlinkTask task = createTask();
        task.startProcessing();
        task.markSucceeded(NOW.plusMinutes(1));

        assertThatThrownBy(task::startProcessing).isInstanceOf(IllegalStateException.class);
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
