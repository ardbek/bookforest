package com.ebook.bookforest.domain.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebook.bookforest.domain.library.dto.LibraryPageResponse;
import com.ebook.bookforest.domain.library.dto.LibraryResponse;
import com.ebook.bookforest.domain.library.entity.Library;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.domain.platform.service.PlatformService;
import com.ebook.bookforest.global.exception.BusinessException;
import com.ebook.bookforest.support.MySqlDataJpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 도서관 목록 검색 동작 검증
 * 내부 의존성이 바뀌어도 동작을 고정하도록 실제 Repository와 컨테이너 DB 사용
 */
@MySqlDataJpaTest
@Import({LibraryService.class, PlatformService.class})
class LibraryServiceIntegrationTest {

    @Autowired
    private LibraryService libraryService;

    @Autowired
    private EntityManager entityManager;

    private Platform libraryPlatform;
    private Platform subscriptionPlatform;

    @BeforeEach
    void setUp() {
        libraryPlatform = savePlatform("library-test", true, false);
        subscriptionPlatform = savePlatform("subscription-test", false, true);
        Platform otherLibraryPlatform = savePlatform("other-library-test", true, false);

        saveLibrary(libraryPlatform, "가람도서관", true);
        saveLibrary(libraryPlatform, "나래도서관 100%", true);
        saveLibrary(libraryPlatform, "나래도서관 1000", true);
        saveLibrary(libraryPlatform, "다온_도서관", true);
        saveLibrary(libraryPlatform, "다온X도서관", true);
        saveLibrary(libraryPlatform, "Seoul Library", true);
        saveLibrary(libraryPlatform, "가람도서관 분관", false);
        saveLibrary(otherLibraryPlatform, "가람도서관 다른 플랫폼", true);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("검색어가 없으면 해당 플랫폼의 노출 도서관만 이름순으로 반환")
    void searchReturnsVisibleLibrariesOfPlatformOrderedByName() {
        // when
        LibraryPageResponse response = search(null, 0, 10);

        // then
        assertThat(response.libraries().content())
            .extracting(LibraryResponse::libraryName)
            .containsExactly(
                "Seoul Library",
                "가람도서관",
                "나래도서관 100%",
                "나래도서관 1000",
                "다온_도서관",
                "다온X도서관"
            );
        assertThat(response.libraries().content())
            .allSatisfy(library -> assertThat(library.platform()).isEqualTo("library-test"));
    }

    @Test
    @DisplayName("공백뿐인 검색어는 검색어가 없는 것과 같은 결과")
    void searchTreatsBlankKeywordAsNoKeyword() {
        // when
        LibraryPageResponse response = search("   ", 0, 10);

        // then
        assertThat(response.libraries().totalElements()).isEqualTo(6);
    }

    @Test
    @DisplayName("검색어 앞뒤 공백을 제거하고 도서관명 부분 일치로 검색")
    void searchTrimsKeywordAndMatchesPartially() {
        // when
        LibraryPageResponse response = search("  나래 ", 0, 10);

        // then
        assertThat(response.libraries().content())
            .extracting(LibraryResponse::libraryName)
            .containsExactly("나래도서관 100%", "나래도서관 1000");
    }

    @Test
    @DisplayName("검색어의 대소문자를 구분하지 않음")
    void searchIgnoresCase() {
        // when
        LibraryPageResponse response = search("seoul", 0, 10);

        // then
        assertThat(response.libraries().content())
            .extracting(LibraryResponse::libraryName)
            .containsExactly("Seoul Library");
    }

    @Test
    @DisplayName("검색어의 %는 와일드카드가 아닌 문자 그대로 검색")
    void searchTreatsPercentAsLiteral() {
        // when
        LibraryPageResponse response = search("100%", 0, 10);

        // then
        assertThat(response.libraries().content())
            .extracting(LibraryResponse::libraryName)
            .containsExactly("나래도서관 100%");
    }

    @Test
    @DisplayName("검색어의 _는 와일드카드가 아닌 문자 그대로 검색")
    void searchTreatsUnderscoreAsLiteral() {
        // when
        LibraryPageResponse response = search("다온_", 0, 10);

        // then
        assertThat(response.libraries().content())
            .extracting(LibraryResponse::libraryName)
            .containsExactly("다온_도서관");
    }

    @Test
    @DisplayName("페이지 크기만큼 반환하고 전체 개수와 마지막 페이지 여부 제공")
    void searchReturnsPageInformation() {
        // when
        LibraryPageResponse firstPage = search(null, 0, 4);
        LibraryPageResponse lastPage = search(null, 1, 4);

        // then
        assertThat(firstPage.libraries().content()).hasSize(4);
        assertThat(firstPage.libraries().totalElements()).isEqualTo(6);
        assertThat(firstPage.libraries().last()).isFalse();
        assertThat(lastPage.libraries().content())
            .extracting(LibraryResponse::libraryName)
            .containsExactly("다온_도서관", "다온X도서관");
        assertThat(lastPage.libraries().last()).isTrue();
    }

    @Test
    @DisplayName("플랫폼이 없으면 platform_not_found 오류")
    void searchThrowsWhenPlatformNotFound() {
        // when, then
        assertThatThrownBy(() -> searchByPlatform(-1L, null))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorCode().code()).isEqualTo("platform_not_found");
                assertThat(exception.getErrorCode().httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            });
    }

    @Test
    @DisplayName("무료 도서관을 지원하지 않는 플랫폼이면 library_not_supported 오류")
    void searchThrowsWhenPlatformDoesNotSupportLibrary() {
        // when, then
        assertThatThrownBy(() -> searchByPlatform(subscriptionPlatform.getId(), null))
            .isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorCode().code()).isEqualTo("library_not_supported");
                assertThat(exception.getErrorCode().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    private LibraryPageResponse search(String keyword, int page, int size) {
        return libraryService.searchLibraries(libraryPlatform.getId(), keyword, PageRequest.of(page, size));
    }

    private LibraryPageResponse searchByPlatform(Long platformId, String keyword) {
        return libraryService.searchLibraries(platformId, keyword, PageRequest.of(0, 10));
    }

    private Platform savePlatform(String platformCode, boolean supportsLibrary, boolean supportsSubscription) {
        Platform platform = Platform.builder()
            .platformCode(platformCode)
            .platformName(platformCode)
            .supportsLibrary(supportsLibrary)
            .supportsSubscription(supportsSubscription)
            .build();
        entityManager.persist(platform);
        return platform;
    }

    private void saveLibrary(Platform platform, String libraryName, boolean visible) {
        Library library = Library.builder()
            .platform(platform)
            .libraryCode(libraryName)
            .libraryName(libraryName)
            .siteUrl("https://library.example")
            .build();
        ReflectionTestUtils.setField(library, "visible", visible);
        entityManager.persist(library);
    }
}
