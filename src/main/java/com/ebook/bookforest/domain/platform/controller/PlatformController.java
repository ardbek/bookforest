package com.ebook.bookforest.domain.platform.controller;

import com.ebook.bookforest.domain.platform.dto.PlatformListResponse;
import com.ebook.bookforest.domain.platform.service.PlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformService platformService;

    @GetMapping
    public PlatformListResponse getPlatforms(
        @RequestParam(name = "isSubscribe", required = false) Boolean isSubscribe
    ) {
        return platformService.getPlatforms(isSubscribe);
    }
}
