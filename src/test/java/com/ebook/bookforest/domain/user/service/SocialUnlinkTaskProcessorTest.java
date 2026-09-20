package com.ebook.bookforest.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebook.bookforest.domain.user.enums.ProviderTokenType;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.global.security.crypto.OAuthTokenCipher;
import com.ebook.bookforest.global.security.oauth.unlink.SocialUnlinkClient;
import com.ebook.bookforest.global.security.oauth.unlink.SocialUnlinkClientException;
import com.ebook.bookforest.global.security.oauth.unlink.SocialUnlinkClientRegistry;
import com.ebook.bookforest.global.security.oauth.unlink.SocialUnlinkCommand;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocialUnlinkTaskProcessorTest {

    @Mock
    private SocialUnlinkTaskStateService stateService;
    @Mock
    private SocialUnlinkClientRegistry clientRegistry;
    @Mock
    private OAuthTokenCipher tokenCipher;
    @Mock
    private SocialUnlinkClient client;
    @InjectMocks
    private SocialUnlinkTaskProcessor processor;

    @Test
    void 네이버_토큰을_복호화해_연동을_해제한다() {
        SocialUnlinkWork work = naverWork("encrypted-token");
        when(stateService.claim(eq(1L), any(LocalDateTime.class)))
            .thenReturn(Optional.of(work));
        when(tokenCipher.decrypt("encrypted-token"))
            .thenReturn("plain-token");
        when(clientRegistry.getClient(SocialProvider.NAVER))
            .thenReturn(client);

        processor.process(1L);

        ArgumentCaptor<SocialUnlinkCommand> captor =
            ArgumentCaptor.forClass(SocialUnlinkCommand.class);
        verify(client).unlink(captor.capture());
        assertEquals("plain-token", captor.getValue().providerToken());
        assertEquals(ProviderTokenType.REFRESH_TOKEN, captor.getValue().providerTokenType());
        verify(stateService).markSucceeded(eq(1L), any(LocalDateTime.class));
    }

    @Test
    void 재시도_가능한_외부_API_오류를_기록한다() {
        when(stateService.claim(eq(1L), any(LocalDateTime.class)))
            .thenReturn(Optional.of(naverWork("encrypted-token")));
        when(tokenCipher.decrypt("encrypted-token"))
            .thenReturn("plain-token");
        when(clientRegistry.getClient(SocialProvider.NAVER))
            .thenReturn(client);
        org.mockito.Mockito.doThrow(new SocialUnlinkClientException(
            true,
            "NAVER_TEMPORARILY_UNAVAILABLE"
        )).when(client).unlink(any(SocialUnlinkCommand.class));

        processor.process(1L);

        verify(stateService).handleFailure(
            eq(1L),
            eq(true),
            eq("NAVER_TEMPORARILY_UNAVAILABLE"),
            any(LocalDateTime.class)
        );
        verify(stateService, never()).markSucceeded(
            eq(1L),
            any(LocalDateTime.class)
        );
    }

    @Test
    void 네이버_토큰이_없으면_영구_실패로_기록한다() {
        when(stateService.claim(eq(1L), any(LocalDateTime.class)))
            .thenReturn(Optional.of(naverWork(null)));

        processor.process(1L);

        verify(stateService).handleFailure(
            eq(1L),
            eq(false),
            eq("INVALID_UNLINK_TASK"),
            any(LocalDateTime.class)
        );
        verify(clientRegistry, never()).getClient(any());
    }

    private SocialUnlinkWork naverWork(String ciphertext) {
        return new SocialUnlinkWork(
            1L,
            SocialProvider.NAVER,
            "naver-user-id",
            ciphertext,
            ProviderTokenType.REFRESH_TOKEN
        );
    }
}
