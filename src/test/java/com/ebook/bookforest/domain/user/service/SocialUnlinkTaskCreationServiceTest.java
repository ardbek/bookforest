package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.user.entity.User;
import static com.ebook.bookforest.support.EntityIds.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

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
        SocialOAuthCredential credential = withId(SocialOAuthCredential.create(naver), 30L);
        credential.updateAccessToken("encrypted-access", null);
        credential.updateRefreshToken("encrypted-refresh");

        given(socialAccountRepository.findAllByUser_Id(1L))
            .willReturn(List.of(kakao, naver));
        given(credentialRepository.findAllBySocialAccount_User_Id(1L))
            .willReturn(List.of(credential));

        ArgumentCaptor<List<SocialUnlinkTask>> captor =
            ArgumentCaptor.forClass(List.class);
        given(taskRepository.saveAllAndFlush(captor.capture()))
            .willReturn(List.of(
                withId(SocialUnlinkTask.createPending(SocialProvider.KAKAO, "kakao-id", null, null, now), 100L),
                withId(SocialUnlinkTask.createPending(SocialProvider.NAVER, "naver-id", null, null, now), 200L)
            ));

        List<Long> taskIds = service.createTasks(1L, now);

        assertThat(taskIds).isEqualTo(List.of(100L, 200L));
        List<SocialUnlinkTask> tasks = captor.getValue();
        SocialUnlinkTask kakaoTask = tasks.get(0);
        SocialUnlinkTask naverTask = tasks.get(1);

        assertThat(kakaoTask.getProviderTokenCiphertext()).isNull();
        assertThat(kakaoTask.getProviderTokenType()).isNull();
        assertThat(naverTask.getProviderTokenCiphertext()).isEqualTo("encrypted-refresh");
        assertThat(naverTask.getProviderTokenType()).isEqualTo(ProviderTokenType.REFRESH_TOKEN);
        assertThat(naverTask.getNextRetryAt()).isEqualTo(now);
    }

    private SocialAccount account(
        Long id,
        SocialProvider provider,
        String subject
    ) {
        SocialAccount account = SocialAccount.builder()
            .user(User.create(null, null, null))
            .socialProvider(provider)
            .providerSubject(subject)
            .build();
        return withId(account, id);
    }
}
