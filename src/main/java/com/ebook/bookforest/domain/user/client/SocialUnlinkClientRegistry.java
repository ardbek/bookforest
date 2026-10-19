package com.ebook.bookforest.domain.user.client;

import com.ebook.bookforest.domain.user.enums.SocialProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 플랫폼별 연동 해제 클라이언트 선택
 */
@Component
@RequiredArgsConstructor
public class SocialUnlinkClientRegistry {

    private final List<SocialUnlinkClient> clients;

    /**
     * 연동 해제 클라이언트 조회(플랫폼별)
     *
     * @throws IllegalArgumentException 지원하는 클라이언트가 없는 경우
     */
    public SocialUnlinkClient getClient(SocialProvider provider) {
        return clients.stream()
            .filter(client -> client.supports(provider))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 플랫폼입니다. " + provider));
    }
}
