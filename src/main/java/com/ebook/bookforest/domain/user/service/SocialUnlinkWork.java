package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.user.enums.ProviderTokenType;
import com.ebook.bookforest.domain.user.enums.SocialProvider;

public record SocialUnlinkWork(
    Long taskId,
    SocialProvider provider,
    String providerSubject,
    String providerTokenCiphertext,
    ProviderTokenType providerTokenType
) {
}
