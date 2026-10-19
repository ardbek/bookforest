package com.ebook.bookforest.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.ebook.bookforest.domain.user.client.SocialUnlinkClient;
import com.ebook.bookforest.domain.user.client.SocialUnlinkClientException;
import com.ebook.bookforest.domain.user.client.SocialUnlinkClientRegistry;
import com.ebook.bookforest.domain.user.client.SocialUnlinkCommand;
import com.ebook.bookforest.domain.user.crypto.OAuthTokenCipher;
import com.ebook.bookforest.domain.user.enums.ProviderTokenType;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
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
        given(stateService.claim(eq(1L), any(LocalDateTime.class)))
            .willReturn(Optional.of(work));
        given(tokenCipher.decrypt("encrypted-token"))
            .willReturn("plain-token");
        given(clientRegistry.getClient(SocialProvider.NAVER))
            .willReturn(client);

        processor.process(1L);

        ArgumentCaptor<SocialUnlinkCommand> captor =
            ArgumentCaptor.forClass(SocialUnlinkCommand.class);
        then(client).should().unlink(captor.capture());
        assertThat(captor.getValue().providerToken()).isEqualTo("plain-token");
        assertThat(captor.getValue().providerTokenType()).isEqualTo(ProviderTokenType.REFRESH_TOKEN);
        then(stateService).should().markSucceeded(eq(1L), any(LocalDateTime.class));
    }

    @Test
    void 재시도_가능한_외부_API_오류를_기록한다() {
        given(stateService.claim(eq(1L), any(LocalDateTime.class)))
            .willReturn(Optional.of(naverWork("encrypted-token")));
        given(tokenCipher.decrypt("encrypted-token"))
            .willReturn("plain-token");
        given(clientRegistry.getClient(SocialProvider.NAVER))
            .willReturn(client);
        willThrow(new SocialUnlinkClientException(
            true,
            "NAVER_TEMPORARILY_UNAVAILABLE"
        )).given(client).unlink(any(SocialUnlinkCommand.class));

        processor.process(1L);

        then(stateService).should().handleFailure(
            eq(1L),
            eq(true),
            eq("NAVER_TEMPORARILY_UNAVAILABLE"),
            any(LocalDateTime.class)
        );
        then(stateService).should(never()).markSucceeded(
            eq(1L),
            any(LocalDateTime.class)
        );
    }

    @Test
    void 네이버_토큰이_없으면_영구_실패로_기록한다() {
        given(stateService.claim(eq(1L), any(LocalDateTime.class)))
            .willReturn(Optional.of(naverWork(null)));

        processor.process(1L);

        then(stateService).should().handleFailure(
            eq(1L),
            eq(false),
            eq("INVALID_UNLINK_TASK"),
            any(LocalDateTime.class)
        );
        then(clientRegistry).should(never()).getClient(any());
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
