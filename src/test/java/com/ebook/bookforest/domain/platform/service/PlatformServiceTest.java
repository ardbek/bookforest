package com.ebook.bookforest.domain.platform.service;

import static com.ebook.bookforest.support.EntityIds.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.ebook.bookforest.domain.platform.dto.PlatformInfo;
import com.ebook.bookforest.domain.platform.dto.PlatformListResponse;
import com.ebook.bookforest.domain.platform.dto.PlatformResponse;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.domain.platform.exception.PlatformErrorCode;
import com.ebook.bookforest.domain.platform.exception.PlatformException;
import com.ebook.bookforest.domain.platform.repository.PlatformRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformServiceTest {

    @Mock
    private PlatformRepository platformRepository;

    @InjectMocks
    private PlatformService platformService;

    @Test
    @DisplayName("구독 플랫폼을 요청하면 구독 지원 플랫폼 목록을 조회 순서대로 반환")
    void getPlatformsReturnsSubscriptionPlatformsWhenSubscriptionRequested() {
        // given
        given(platformRepository.findAllBySupportsSubscriptionTrueOrderByPlatformNameAsc())
            .willReturn(List.of(platform(1L, "MILLIE", "밀리의 서재", false, true)));

        // when
        PlatformListResponse response = platformService.getPlatforms(true);

        // then
        assertThat(response).isEqualTo(new PlatformListResponse(List.of(
            new PlatformResponse(1L, "MILLIE", "밀리의 서재", null, true)
        )));
    }

    @Test
    @DisplayName("무료 도서관 플랫폼을 요청하면 도서관 지원 플랫폼 목록을 조회 순서대로 반환")
    void getPlatformsReturnsLibraryPlatformsWhenSubscriptionNotRequested() {
        // given
        given(platformRepository.findAllBySupportsLibraryTrueOrderByPlatformNameAsc())
            .willReturn(List.of(
                platform(2L, "KYOBO", "교보문고", true, false),
                platform(3L, "YES24", "예스24", true, false)
            ));

        // when
        PlatformListResponse response = platformService.getPlatforms(false);

        // then
        assertThat(response).isEqualTo(new PlatformListResponse(List.of(
            new PlatformResponse(2L, "KYOBO", "교보문고", null, false),
            new PlatformResponse(3L, "YES24", "예스24", null, false)
        )));
    }

    @Test
    @DisplayName("구독 여부를 지정하지 않으면 전체 플랫폼 목록을 반환")
    void getPlatformsReturnsAllPlatformsWhenSubscriptionNotSpecified() {
        // given
        given(platformRepository.findAllByOrderByPlatformNameAsc())
            .willReturn(List.of(
                platform(2L, "KYOBO", "교보문고", true, false),
                platform(1L, "MILLIE", "밀리의 서재", false, true)
            ));

        // when
        PlatformListResponse response = platformService.getPlatforms(null);

        // then
        assertThat(response).isEqualTo(new PlatformListResponse(List.of(
            new PlatformResponse(2L, "KYOBO", "교보문고", null, false),
            new PlatformResponse(1L, "MILLIE", "밀리의 서재", null, true)
        )));
    }

    @Test
    @DisplayName("조회된 플랫폼이 없으면 빈 목록 반환")
    void getPlatformsReturnsEmptyListWhenNoPlatform() {
        // given
        given(platformRepository.findAllByOrderByPlatformNameAsc()).willReturn(List.of());

        // when
        PlatformListResponse response = platformService.getPlatforms(null);

        // then
        assertThat(response.platforms()).isEmpty();
    }

    @Test
    @DisplayName("플랫폼이 있으면 다른 도메인용 조회 정보 반환")
    void getPlatformInfoReturnsInfoWhenPlatformExists() {
        // given
        given(platformRepository.findById(2L))
            .willReturn(Optional.of(platform(2L, "KYOBO", "교보문고", true, false)));

        // when
        PlatformInfo info = platformService.getPlatformInfo(2L);

        // then
        assertThat(info).isEqualTo(new PlatformInfo(2L, "KYOBO", "교보문고", true, false));
    }

    @Test
    @DisplayName("플랫폼이 없으면 PLATFORM_NOT_FOUND 예외 발생")
    void getPlatformInfoThrowsWhenPlatformNotFound() {
        // given
        given(platformRepository.findById(99L)).willReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> platformService.getPlatformInfo(99L))
            .isInstanceOfSatisfying(
                PlatformException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(PlatformErrorCode.PLATFORM_NOT_FOUND)
            );
    }

    private Platform platform(
        Long id,
        String platformCode,
        String platformName,
        boolean supportsLibrary,
        boolean supportsSubscription
    ) {
        Platform platform = Platform.builder()
            .platformCode(platformCode)
            .platformName(platformName)
            .supportsLibrary(supportsLibrary)
            .supportsSubscription(supportsSubscription)
            .build();
        return withId(platform, id);
    }
}
