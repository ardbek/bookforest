package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.support.SocialUnlinkTaskFixture;
import static com.ebook.bookforest.support.EntityIds.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

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
        task = withId(SocialUnlinkTask.createPending(
            SocialProvider.NAVER,
            "naver-user-id",
            "encrypted-token",
            ProviderTokenType.REFRESH_TOKEN,
            NOW
        ), TASK_ID);

        given(taskRepository.findByIdForUpdate(TASK_ID))
            .willReturn(Optional.of(task));
    }

    @Test
    void 처리_가능한_작업을_선점한다() {
        Optional<SocialUnlinkWork> claimed = service.claim(TASK_ID, NOW);

        assertThat(claimed.isPresent()).isTrue();
        assertThat(claimed.orElseThrow().taskId()).isEqualTo(TASK_ID);
        assertThat(task.getStatus()).isEqualTo(SocialUnlinkStatus.PROCESSING);
    }

    @Test
    void 재시도_시간이_되지_않은_작업은_선점하지_않는다() {
        Optional<SocialUnlinkWork> claimed = service.claim(
            TASK_ID,
            NOW.minusSeconds(1)
        );

        assertThat(claimed).isEmpty();
        assertThat(task.getStatus()).isEqualTo(SocialUnlinkStatus.PENDING);
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

            assertThat(task.getStatus()).isEqualTo(SocialUnlinkStatus.RETRY_WAITING);
            assertThat(task.getRetryCount()).isEqualTo(retry);
            assertThat(task.getNextRetryAt()).isEqualTo(failedAt.plusMinutes(1L << (retry - 1)));
        }

        task.startProcessing();
        service.handleFailure(
            TASK_ID,
            true,
            "TEMPORARY_ERROR",
            NOW.plusHours(6)
        );

        assertThat(task.getStatus()).isEqualTo(SocialUnlinkStatus.FAILED);
        assertThat(task.getRetryCount()).isEqualTo(5);
        assertThat(task.getLastErrorCode()).isEqualTo("TEMPORARY_ERROR");
        assertThat(task.isReady(NOW.plusDays(1))).isFalse();
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

        assertThat(task.getStatus()).isEqualTo(SocialUnlinkStatus.FAILED);
        assertThat(task.getRetryCount()).isEqualTo(0);
        assertThat(task.getLastErrorCode()).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void 제한_시간을_넘긴_처리중_작업은_재시도를_예약한다() {
        task.startProcessing();

        boolean recovered = service.recoverStaleProcessing(TASK_ID, NOW);

        assertThat(recovered).isTrue();
        assertThat(task.getStatus()).isEqualTo(SocialUnlinkStatus.RETRY_WAITING);
        assertThat(task.getRetryCount()).isEqualTo(1);
        assertThat(task.getLastErrorCode()).isEqualTo("PROCESSING_TIMEOUT");
        assertThat(task.getNextRetryAt()).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    void 처리중이_아닌_작업은_복구하지_않는다() {
        boolean recovered = service.recoverStaleProcessing(TASK_ID, NOW);

        assertThat(recovered).isFalse();
        assertThat(task.getStatus()).isEqualTo(SocialUnlinkStatus.PENDING);
        assertThat(task.getRetryCount()).isEqualTo(0);
    }

    @Test
    void 재시도_한도에_도달한_처리중_작업은_실패로_종료한다() {
        task = SocialUnlinkTaskFixture.task(
            TASK_ID,
            SocialProvider.NAVER,
            "naver-user-id",
            "encrypted-token",
            ProviderTokenType.REFRESH_TOKEN,
            SocialUnlinkStatus.PROCESSING,
            5
        );
        given(taskRepository.findByIdForUpdate(TASK_ID))
            .willReturn(Optional.of(task));

        boolean recovered = service.recoverStaleProcessing(TASK_ID, NOW);

        assertThat(recovered).isTrue();
        assertThat(task.getStatus()).isEqualTo(SocialUnlinkStatus.FAILED);
        assertThat(task.getRetryCount()).isEqualTo(5);
        assertThat(task.getLastErrorCode()).isEqualTo("PROCESSING_TIMEOUT");
        assertThat(task.getNextRetryAt()).isNull();
    }
}
