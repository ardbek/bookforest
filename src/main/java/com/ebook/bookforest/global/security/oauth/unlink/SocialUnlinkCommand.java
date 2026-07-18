package com.ebook.bookforest.global.security.oauth.unlink;

import com.ebook.bookforest.domain.user.enums.ProviderTokenType;
import com.ebook.bookforest.domain.user.enums.SocialProvider;

public record SocialUnlinkCommand(
    SocialProvider provider,
    String providerSubject,
    String providerToken, // 복호화 토큰
    ProviderTokenType providerTokenType
) {

}
