package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.user.enums.ProviderTokenType;
import com.ebook.bookforest.domain.user.enums.SocialProvider;

/**
 * 선점한 연동 해제 작업의 처리 정보
 * 트랜잭션 밖 외부 API 호출에서 엔티티 대신 사용
 */
public record SocialUnlinkWork(
    Long taskId,
    SocialProvider provider,
    String providerSubject,
    String providerTokenCiphertext,
    ProviderTokenType providerTokenType
) {
}
