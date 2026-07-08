package com.ebook.bookforest.domain.platform.repository;

import com.ebook.bookforest.domain.platform.entity.Platform;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 플랫폼 정보 조회 Repository
 */
public interface PlatformRepository extends JpaRepository<Platform, Long> {

    /**
     * 모든 플랫폼 조회
     * @return
     */
    List<Platform> findAllByOrderByPlatformNameAsc();

    /**
     * 무료 플랫폼 조회
     * @return
     */
    List<Platform> findAllBySupportsLibraryTrueOrderByPlatformNameAsc();

    /**
     * 구독 플랫폼 조회
     * @return
     */
    List<Platform> findAllBySupportsSubscriptionTrueOrderByPlatformNameAsc();
}
