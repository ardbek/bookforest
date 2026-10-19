package com.ebook.bookforest.domain.user.client;

import com.ebook.bookforest.domain.user.enums.ProviderTokenType;
import com.ebook.bookforest.domain.user.enums.SocialProvider;

/**
 * 연동 해제 요청 값
 *
 * @param providerToken 복호화한 소셜 플랫폼 토큰, Admin Key로 해제하는 카카오는 null
 * @param providerTokenType 토큰 종류, 카카오는 null
 */
public record SocialUnlinkCommand(
    SocialProvider provider,
    String providerSubject,
    String providerToken,
    ProviderTokenType providerTokenType
) {
}
