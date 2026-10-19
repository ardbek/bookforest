package com.ebook.bookforest.domain.platform.service;

import com.ebook.bookforest.domain.platform.dto.PlatformInfo;
import com.ebook.bookforest.domain.platform.dto.PlatformListResponse;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.domain.platform.exception.PlatformErrorCode;
import com.ebook.bookforest.domain.platform.exception.PlatformException;
import com.ebook.bookforest.domain.platform.repository.PlatformRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플랫폼 조회 서비스
 * 플랫폼 데이터는 수집기와 시드 SQL이 등록하므로 조회만 담당
 */
@Service
@RequiredArgsConstructor
public class PlatformService {

    private final PlatformRepository platformRepository;

    /**
     * 플랫폼 목록 조회(이름순)
     *
     * <p>구독 여부별 조회 범위
     * <ul>
     *   <li>true: 구독 플랫폼</li>
     *   <li>false: 무료 도서관 플랫폼</li>
     *   <li>null: 전체 플랫폼</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public PlatformListResponse getPlatforms(Boolean supportsSubscription) {
        List<Platform> platforms;

        if (supportsSubscription == null) {
            platforms = platformRepository.findAllByOrderByPlatformNameAsc();
        } else if (supportsSubscription) {
            platforms = platformRepository.findAllBySupportsSubscriptionTrueOrderByPlatformNameAsc();
        } else {
            platforms = platformRepository.findAllBySupportsLibraryTrueOrderByPlatformNameAsc();
        }

        return PlatformListResponse.from(platforms);
    }

    /** 플랫폼 정보 목록 조회(전체, 이름순, 다른 도메인 제공용) */
    @Transactional(readOnly = true)
    public List<PlatformInfo> getPlatformInfos() {
        return platformRepository.findAllByOrderByPlatformNameAsc()
            .stream()
            .map(PlatformInfo::from)
            .toList();
    }

    /**
     * 플랫폼 정보 단건 조회(다른 도메인 제공용)
     * 없을 때 어떤 오류로 처리할지는 호출하는 쪽이 결정
     */
    @Transactional(readOnly = true)
    public Optional<PlatformInfo> findPlatformInfo(Long platformId) {
        return platformRepository.findById(platformId).map(PlatformInfo::from);
    }

    /**
     * 플랫폼 정보 단건 조회(다른 도메인 제공용)
     *
     * @throws PlatformException 플랫폼이 없는 경우 PLATFORM_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public PlatformInfo getPlatformInfo(Long platformId) {
        return platformRepository.findById(platformId)
            .map(PlatformInfo::from)
            .orElseThrow(() -> new PlatformException(PlatformErrorCode.PLATFORM_NOT_FOUND));
    }
}
