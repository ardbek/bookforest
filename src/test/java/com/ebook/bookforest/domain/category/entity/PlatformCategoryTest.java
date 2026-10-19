package com.ebook.bookforest.domain.category.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebook.bookforest.domain.category.enums.MappingStatus;
import com.ebook.bookforest.domain.platform.entity.Platform;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformCategoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 12, 0);

    @Test
    @DisplayName("새 플랫폼 카테고리는 미매핑, 활성, 깊이 1, 정렬 순서 0 상태")
    void newPlatformCategoryHasDefaultState() {
        // when
        PlatformCategory category = platformCategory();

        // then
        assertThat(category.getMappingStatus()).isEqualTo(MappingStatus.UNMAPPED);
        assertThat(category.isActive()).isTrue();
        assertThat(category.getDepth()).isEqualTo(1);
        assertThat(category.getSortOrder()).isZero();
        assertThat(category.getStandardCategory()).isNull();
    }

    @Test
    @DisplayName("자동 매핑하면 표준 카테고리와 신뢰도를 지정하고 자동 매핑 상태로 변경")
    void applyAutoMappingSetsCandidate() {
        // given
        PlatformCategory category = platformCategory();
        StandardCategory standardCategory = standardCategory();

        // when
        category.applyAutoMapping(standardCategory, new BigDecimal("0.85"));

        // then
        assertThat(category.getStandardCategory()).isSameAs(standardCategory);
        assertThat(category.getMappingConfidence()).isEqualByComparingTo("0.85");
        assertThat(category.getMappingStatus()).isEqualTo(MappingStatus.AUTO_MAPPED);
    }

    @Test
    @DisplayName("매핑을 확정하면 확정자와 시각을 기록하고 확정 상태로 변경")
    void confirmMappingRecordsAdmin() {
        // given
        PlatformCategory category = platformCategory();
        category.applyAutoMapping(standardCategory(), new BigDecimal("0.85"));
        StandardCategory confirmedCategory = standardCategory();

        // when
        category.confirmMapping(confirmedCategory, "admin", NOW);

        // then
        assertThat(category.getStandardCategory()).isSameAs(confirmedCategory);
        assertThat(category.getMappingStatus()).isEqualTo(MappingStatus.CONFIRMED);
        assertThat(category.getMappedBy()).isEqualTo("admin");
        assertThat(category.getMappedAt()).isEqualTo(NOW);
        assertThat(category.getMappingConfidence()).isEqualByComparingTo("0.85");
    }

    @Test
    @DisplayName("매핑에서 제외하면 표준 카테고리와 신뢰도를 비우고 제외 상태로 변경")
    void ignoreMappingClearsCandidate() {
        // given
        PlatformCategory category = platformCategory();
        category.applyAutoMapping(standardCategory(), new BigDecimal("0.85"));

        // when
        category.ignoreMapping("admin", NOW);

        // then
        assertThat(category.getStandardCategory()).isNull();
        assertThat(category.getMappingConfidence()).isNull();
        assertThat(category.getMappingStatus()).isEqualTo(MappingStatus.IGNORED);
        assertThat(category.getMappedBy()).isEqualTo("admin");
        assertThat(category.getMappedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("비활성화 후 다시 활성화하면 활성 상태")
    void deactivateAndActivateToggleActive() {
        // given
        PlatformCategory category = platformCategory();

        // when
        category.deactivate();
        boolean deactivated = category.isActive();
        category.activate();

        // then
        assertThat(deactivated).isFalse();
        assertThat(category.isActive()).isTrue();
    }

    private PlatformCategory platformCategory() {
        Platform platform = Platform.builder()
            .platformCode("KYOBO")
            .platformName("교보문고")
            .supportsLibrary(true)
            .build();
        return PlatformCategory.builder()
            .platform(platform)
            .categoryCode("31")
            .categoryName("유아")
            .build();
    }

    private StandardCategory standardCategory() {
        return StandardCategory.builder()
            .categoryCode("CHILD")
            .categoryName("유아")
            .build();
    }
}
