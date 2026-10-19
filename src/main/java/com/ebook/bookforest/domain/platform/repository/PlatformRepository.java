package com.ebook.bookforest.domain.platform.repository;

import com.ebook.bookforest.domain.platform.entity.Platform;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 플랫폼 조회 Repository
 */
public interface PlatformRepository extends JpaRepository<Platform, Long> {

    /** 플랫폼 조회(전체, 이름순) */
    List<Platform> findAllByOrderByPlatformNameAsc();

    /** 플랫폼 조회(무료 전자도서관 지원, 이름순) */
    List<Platform> findAllBySupportsLibraryTrueOrderByPlatformNameAsc();

    /** 플랫폼 조회(구독 지원, 이름순) */
    List<Platform> findAllBySupportsSubscriptionTrueOrderByPlatformNameAsc();
}
