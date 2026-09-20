package com.ebook.bookforest.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.ebook.bookforest.domain.user.entity.SocialAccount;
import com.ebook.bookforest.domain.user.entity.SocialOAuthCredential;
import com.ebook.bookforest.domain.user.entity.SocialUnlinkTask;
import com.ebook.bookforest.domain.user.enums.ProviderTokenType;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.repository.SocialAccountRepository;
import com.ebook.bookforest.domain.user.repository.SocialOAuthCredentialRepository;
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
class SocialUnlinkTaskCreationServiceTest {

    @Mock
    private SocialAccountRepository socialAccountRepository;
    @Mock
    private SocialOAuthCredentialRepository credentialRepository;
    @Mock
    private SocialUnlinkTaskRepository taskRepository;
    @InjectMocks
    private SocialUnlinkTaskCreationService service;

    @Test
    @SuppressWarnings("unchecked")
    void 카카오와_네이버_연동_해제_작업을_생성한다() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 18, 12, 0);
        SocialAccount kakao = account(10L, SocialProvider.KAKAO, "kakao-id");
        SocialAccount naver = account(20L, SocialProvider.NAVER, "naver-id");
        SocialOAuthCredential credential = SocialOAuthCredential.builder()
            .id(30L)
            .socialAccount(naver)
            .accessTokenCiphertext("encrypted-access")
            .refreshTokenCiphertext("encrypted-refresh")
            .build();

        when(socialAccountRepository.findAllByUser_Id(1L))
            .thenReturn(List.of(kakao, naver));
        when(credentialRepository.findAllBySocialAccount_User_Id(1L))
            .thenReturn(List.of(credential));

        ArgumentCaptor<List<SocialUnlinkTask>> captor =
            ArgumentCaptor.forClass(List.class);
        when(taskRepository.saveAllAndFlush(captor.capture()))
            .thenReturn(List.of(
                SocialUnlinkTask.builder().id(100L).build(),
                SocialUnlinkTask.builder().id(200L).build()
            ));

        List<Long> taskIds = service.createTasks(1L, now);

        assertEquals(List.of(100L, 200L), taskIds);
        List<SocialUnlinkTask> tasks = captor.getValue();
        SocialUnlinkTask kakaoTask = tasks.get(0);
        SocialUnlinkTask naverTask = tasks.get(1);

        assertNull(kakaoTask.getProviderTokenCiphertext());
        assertNull(kakaoTask.getProviderTokenType());
        assertEquals("encrypted-refresh", naverTask.getProviderTokenCiphertext());
        assertEquals(ProviderTokenType.REFRESH_TOKEN, naverTask.getProviderTokenType());
        assertEquals(now, naverTask.getNextRetryAt());
    }

    private SocialAccount account(
        Long id,
        SocialProvider provider,
        String subject
    ) {
        return SocialAccount.builder()
            .id(id)
            .socialProvider(provider)
            .providerSubject(subject)
            .build();
    }
}
