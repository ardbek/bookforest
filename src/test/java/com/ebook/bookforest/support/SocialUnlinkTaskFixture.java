package com.ebook.bookforest.support;

import static com.ebook.bookforest.support.EntityIds.withId;

import com.ebook.bookforest.domain.user.entity.SocialUnlinkTask;
import com.ebook.bookforest.domain.user.enums.ProviderTokenType;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.enums.SocialUnlinkStatus;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 연동 해제 작업 테스트 도우미
 * 공개 생성 경로로 만들기 번거로운 특정 상태(처리 중, 재시도 횟수 등)를 직접 지정할 때만 사용
 */
public final class SocialUnlinkTaskFixture {

    private SocialUnlinkTaskFixture() {
    }

    /** 지정한 상태와 재시도 횟수의 작업, 처리 가능 시각은 비움 */
    public static SocialUnlinkTask task(
        Long id,
        SocialProvider provider,
        String providerSubject,
        String providerTokenCiphertext,
        ProviderTokenType providerTokenType,
        SocialUnlinkStatus status,
        int retryCount
    ) {
        SocialUnlinkTask task = SocialUnlinkTask.createPending(
            provider,
            providerSubject,
            providerTokenCiphertext,
            providerTokenType,
            LocalDateTime.now()
        );
        ReflectionTestUtils.setField(task, "status", status);
        ReflectionTestUtils.setField(task, "retryCount", retryCount);
        ReflectionTestUtils.setField(task, "nextRetryAt", null);
        return withId(task, id);
    }
}
