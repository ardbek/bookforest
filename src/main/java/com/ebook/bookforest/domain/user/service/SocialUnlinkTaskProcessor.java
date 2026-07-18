package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.global.security.crypto.OAuthTokenCipher;
import com.ebook.bookforest.global.security.crypto.OAuthTokenCryptoException;
import com.ebook.bookforest.global.security.oauth.unlink.SocialUnlinkClient;
import com.ebook.bookforest.global.security.oauth.unlink.SocialUnlinkClientException;
import com.ebook.bookforest.global.security.oauth.unlink.SocialUnlinkClientRegistry;
import com.ebook.bookforest.global.security.oauth.unlink.SocialUnlinkCommand;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialUnlinkTaskProcessor {

    private final SocialUnlinkTaskStateService stateService;
    private final SocialUnlinkClientRegistry clientRegistry;
    private final OAuthTokenCipher tokenCipher;

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
                "PROVIDER_TOKEN_DECRYPT_FAILED",
                LocalDateTime.now()
            );
        } catch (IllegalArgumentException exception) {
            stateService.handleFailure(
                taskId,
                false,
                "INVALID_UNLINK_TASK",
                LocalDateTime.now()
            );
        } catch (RuntimeException exception) {
            log.error(
                "소셜 연동 해제 작업 중 예상하지 못한 오류가 발생했습니다. taskId={}",
                taskId,
                exception
            );

            stateService.handleFailure(
                taskId,
                true,
                "UNEXPECTED_UNLINK_ERROR",
                LocalDateTime.now()
            );
        }
    }

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
