package com.ebook.bookforest.global.security.oauth.unlink;

import com.ebook.bookforest.domain.user.enums.SocialProvider;

public interface SocialUnlinkClient {

    boolean supports(SocialProvider provider);

    void unlink(SocialUnlinkCommand command);

}
