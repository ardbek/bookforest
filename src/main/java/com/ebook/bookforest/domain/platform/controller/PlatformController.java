package com.ebook.bookforest.domain.platform.controller;

import com.ebook.bookforest.domain.platform.dto.PlatformListResponse;
import com.ebook.bookforest.domain.platform.service.PlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 플랫폼 조회 API
 */
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformService platformService;

    /**
     * 플랫폼 목록 조회(이름순)
     *
     * <p>요청 파라미터 isSubscribe별 조회 범위
     * <ul>
     *   <li>true: 구독 플랫폼</li>
     *   <li>false: 무료 도서관 플랫폼</li>
     *   <li>미지정: 전체 플랫폼</li>
     * </ul>
     */
    @GetMapping
    public PlatformListResponse getPlatforms(
        @RequestParam(name = "isSubscribe", required = false) Boolean supportsSubscription
    ) {
        return platformService.getPlatforms(supportsSubscription);
    }
}
