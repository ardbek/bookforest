package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.user.client.SocialUnlinkClient;
import com.ebook.bookforest.domain.user.client.SocialUnlinkClientException;
import com.ebook.bookforest.domain.user.client.SocialUnlinkClientRegistry;
import com.ebook.bookforest.domain.user.client.SocialUnlinkCommand;
import com.ebook.bookforest.domain.user.crypto.OAuthTokenCipher;
import com.ebook.bookforest.domain.user.crypto.OAuthTokenCryptoException;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 소셜 연동 해제 작업 실행기
 * 상태 변경은 짧은 트랜잭션으로, 외부 API 호출은 트랜잭션 밖에서 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialUnlinkTaskProcessor {

    /** 작업의 last_error_code 컬럼에 저장하는 실패 원인 코드 */
    private static final String TOKEN_DECRYPT_FAILED = "PROVIDER_TOKEN_DECRYPT_FAILED";
    private static final String INVALID_UNLINK_TASK = "INVALID_UNLINK_TASK";
    private static final String UNEXPECTED_UNLINK_ERROR = "UNEXPECTED_UNLINK_ERROR";

    private final SocialUnlinkTaskStateService stateService;
    private final SocialUnlinkClientRegistry clientRegistry;
    private final OAuthTokenCipher tokenCipher;

    /**
     * 연동 해제 작업 실행
     * 다른 서버가 이미 가져갔거나 처리할 시점이 아닌 작업은 건너뜀
     */
    public void process(Long taskId) {
        SocialUnlinkWork work = stateService
            .claim(taskId, LocalDateTime.now())
            .orElse(null);

        if (work == null) {
            return;
        }

        try {
            SocialUnlinkCommand command = createCommand(work);
            SocialUnlinkClient client = clientRegistry.getClient(work.provider());

            client.unlink(command);
            stateService.markSucceeded(taskId, LocalDateTime.now());
        } catch (SocialUnlinkClientException exception) {
            stateService.handleFailure(
                taskId,
                exception.isRetryable(),
                exception.getErrorCode(),
                LocalDateTime.now()
            );
        } catch (OAuthTokenCryptoException exception) {
            stateService.handleFailure(
                taskId,
                false,
                TOKEN_DECRYPT_FAILED,
                LocalDateTime.now()
            );
        } catch (IllegalArgumentException exception) {
            stateService.handleFailure(
                taskId,
                false,
                INVALID_UNLINK_TASK,
                LocalDateTime.now()
            );
        } catch (RuntimeException exception) {
            log.error("소셜 연동 해제 처리 중 예상하지 못한 오류: taskId={}", taskId, exception);

            stateService.handleFailure(
                taskId,
                true,
                UNEXPECTED_UNLINK_ERROR,
                LocalDateTime.now()
            );
        }
    }

    /** 카카오는 Admin Key로 해제하므로 토큰 없이, 네이버는 저장해 둔 토큰을 복호화해 명령 생성 */
    private SocialUnlinkCommand createCommand(SocialUnlinkWork work) {
        if (work.provider() == SocialProvider.KAKAO) {
            return new SocialUnlinkCommand(
                work.provider(),
                work.providerSubject(),
                null,
                null
            );
        }

        if (
            work.providerTokenCiphertext() == null
                || work.providerTokenCiphertext().isBlank()
                || work.providerTokenType() == null
        ) {
            throw new IllegalArgumentException("네이버 연동 해제 토큰이 없습니다.");
        }

        String providerToken = tokenCipher.decrypt(
            work.providerTokenCiphertext()
        );

        return new SocialUnlinkCommand(
            work.provider(),
            work.providerSubject(),
            providerToken,
            work.providerTokenType()
        );
    }
}
