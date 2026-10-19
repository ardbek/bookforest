package com.ebook.bookforest.domain.user.service;

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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 시 소셜 연동 해제 작업 생성 서비스
 * 외부 API 호출은 탈퇴 커밋 이후에 하므로 여기서는 작업만 기록
 */
@Service
@RequiredArgsConstructor
public class SocialUnlinkTaskCreationService {

    private final SocialAccountRepository socialAccountRepository;
    private final SocialOAuthCredentialRepository credentialRepository;
    private final SocialUnlinkTaskRepository taskRepository;

    /**
     * 사용자의 소셜 계정별 연동 해제 작업 생성
     * 탈퇴와 같은 트랜잭션에서만 호출되도록 MANDATORY 전파 사용
     *
     * @return 생성한 작업 ID 목록, 탈퇴 커밋 후 이벤트로 처리
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> createTasks(Long userId, LocalDateTime now) {
        List<SocialAccount> accounts =
            socialAccountRepository.findAllByUser_Id(userId);

        Map<Long, SocialOAuthCredential> credentials = credentialRepository
            .findAllBySocialAccount_User_Id(userId)
            .stream()
            .collect(Collectors.toMap(
                credential -> credential.getSocialAccount().getId(),
                Function.identity()
            ));

        List<SocialUnlinkTask> tasks = accounts.stream()
            .map(account -> createTask(
                account,
                credentials.get(account.getId()),
                now
            ))
            .toList();

        return taskRepository.saveAllAndFlush(tasks)
            .stream()
            .map(SocialUnlinkTask::getId)
            .toList();
    }

    private SocialUnlinkTask createTask(
        SocialAccount account,
        SocialOAuthCredential credential,
        LocalDateTime now
    ) {
        TokenSnapshot token = selectToken(
            account.getSocialProvider(),
            credential
        );

        return SocialUnlinkTask.createPending(
            account.getSocialProvider(),
            account.getProviderSubject(),
            token.ciphertext(),
            token.type(),
            now
        );
    }

    /** 네이버 연동 해제용 토큰 선택, 유효 기간이 긴 Refresh Token 우선 */
    private TokenSnapshot selectToken(
        SocialProvider provider,
        SocialOAuthCredential credential
    ) {
        if (provider == SocialProvider.KAKAO || credential == null) {
            return new TokenSnapshot(null, null);
        }

        if (hasText(credential.getRefreshTokenCiphertext())) {
            return new TokenSnapshot(
                credential.getRefreshTokenCiphertext(),
                ProviderTokenType.REFRESH_TOKEN
            );
        }

        return new TokenSnapshot(
            credential.getAccessTokenCiphertext(),
            ProviderTokenType.ACCESS_TOKEN
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record TokenSnapshot(
        String ciphertext,
        ProviderTokenType type
    ) {
    }
}
