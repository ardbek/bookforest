package com.ebook.bookforest.domain.platform.service;

import com.ebook.bookforest.domain.platform.dto.PlatformListResponse;
import com.ebook.bookforest.domain.platform.dto.PlatformResponse;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.domain.platform.repository.PlatformRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformService {

    private final PlatformRepository platformRepository;

    /**
     * 플랫폼 정보 조회
     * @param isSubscribe
     * @return
     */
    @Transactional(readOnly = true)
    public PlatformListResponse getPlatforms(Boolean isSubscribe) {
        List<Platform> platforms;

        if (Boolean.TRUE.equals(isSubscribe)) {
            platforms = platformRepository.findAllBySupportsSubscriptionTrueOrderByPlatformNameAsc();
        } else if (Boolean.FALSE.equals(isSubscribe)) {
            platforms = platformRepository.findAllBySupportsLibraryTrueOrderByPlatformNameAsc();
        } else {
            platforms = platformRepository.findAllByOrderByPlatformNameAsc();
        }

        return new PlatformListResponse(
            platforms.stream()
                .map(PlatformResponse::from)
                .toList()
        );
    }
}
