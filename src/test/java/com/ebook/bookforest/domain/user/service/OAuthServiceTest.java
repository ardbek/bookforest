package com.ebook.bookforest.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.exception.SocialUnlinkInProgressException;
import com.ebook.bookforest.domain.user.repository.SocialAccountRepository;
import com.ebook.bookforest.domain.user.repository.SocialUnlinkTaskRepository;
import com.ebook.bookforest.domain.user.repository.UserRepository;
import com.ebook.bookforest.global.security.oauth.OAuth2Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SocialAccountRepository socialAccountRepository;
    @Mock
    private SocialUnlinkTaskRepository unlinkTaskRepository;
    @Mock
    private OAuth2Response response;
    @InjectMocks
    private OAuthService service;

    @Test
    void 연동_해제중인_소셜_계정은_로그인할_수_없다() {
        when(response.getProvider()).thenReturn(SocialProvider.NAVER);
        when(response.getProviderSubject()).thenReturn("naver-user-id");
        when(unlinkTaskRepository
            .existsBySocialProviderAndProviderSubjectAndStatusIn(
                org.mockito.ArgumentMatchers.eq(SocialProvider.NAVER),
                org.mockito.ArgumentMatchers.eq("naver-user-id"),
                anyCollection()
            )).thenReturn(true);

        assertThrows(
            SocialUnlinkInProgressException.class,
            () -> service.loginOrSignUp(response)
        );

        verify(socialAccountRepository, never())
            .findBySocialProviderAndProviderSubject(
                SocialProvider.NAVER,
                "naver-user-id"
            );
        verify(userRepository, never()).save(
            org.mockito.ArgumentMatchers.any()
        );
    }
}
