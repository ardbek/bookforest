package com.ebook.bookforest.global.security.oauth.unlink;

import com.ebook.bookforest.domain.user.enums.SocialProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SocialUnlinkClientRegistry {

    private final List<SocialUnlinkClient> clients;

    public SocialUnlinkClient getClient(SocialProvider provider) {
        return clients.stream()
            .filter(client -> client.supports(provider))
            .findFirst()
            .orElseThrow(() ->
                new IllegalArgumentException("지원하지 않는 플랫폼입니다. " + provider)
            );
    }

}
