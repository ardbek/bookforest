package com.ebook.bookforest.domain.ranking.service;

import static com.ebook.bookforest.support.EntityIds.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.repository.BookPlatformSummary;
import com.ebook.bookforest.domain.book.repository.BookRepository;
import com.ebook.bookforest.domain.book.service.BookService;
import com.ebook.bookforest.domain.platform.entity.Platform;
import com.ebook.bookforest.domain.ranking.client.DataLibraryClient;
import com.ebook.bookforest.domain.ranking.dto.RankingResponse;
import com.ebook.bookforest.domain.ranking.enums.RankingType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;


class RankingServiceTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final LocalDate today = LocalDate.of(2026, 9, 17);
    private final DataLibraryClient client = mock(DataLibraryClient.class);
    private final BookRepository books = mock(BookRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Map<String, String> cache = new HashMap<>();
    private final RankingService service = new RankingService(client, new BookService(books), redis, mapper);

    RankingServiceTest() {
        given(client.isConfigured()).willReturn(true);
        given(redis.opsForValue()).willReturn(values);
        given(redis.hasKey(anyString())).willAnswer(call -> cache.containsKey(call.getArgument(0)));
        given(values.get(anyString())).willAnswer(call -> cache.get(call.getArgument(0)));
        willAnswer(call -> { cache.put(call.getArgument(0), call.getArgument(1)); return null; })
            .given(values).set(anyString(), anyString());
    }

    @Test
    void allEmptyTrendDaysKeepExistingCache() {
        String previous = mapper.writeValueAsString(new RankingResponse(null, today.minusDays(1), List.of(
            new RankingResponse.Book(1, 1L, "9780000000001", "이전 도서", "저자", null, null, null, List.of()))));
        cache.put(RankingType.TRENDING.cacheKey(), previous);
        // 모든 날짜가 비어 있으면 클라이언트가 예외를 던짐(DataLibraryClientTest에서 검증)
        given(client.fetch(eq(RankingType.TRENDING), any()))
            .willThrow(new IllegalArgumentException("순위 도서가 있는 급상승 날짜가 없습니다."));
        service.refresh(RankingType.TRENDING);
        assertThat(cache.get(RankingType.TRENDING.cacheKey())).isEqualTo(previous);
        then(values).should(never()).set(anyString(), anyString());
        then(books).shouldHaveNoInteractions();
    }

    @Test
    void retainsAllProviderBooksAndMetadataWhileAddingMatchedPlatforms() {
        var library = withId(Platform.builder().platformName("도서관").supportsLibrary(true).build(), 1L);
        var subscription = withId(Platform.builder().platformName("구독").supportsSubscription(true).build(), 2L);
        var first = withId(BookOrigin.builder().platform(library).platformBookId("b-10").isbn13Raw("9781234567890")
            .titleRaw("DB 도서명").titleNorm("DB도서명").authorRaw("DB 저자").publisherRaw("DB 출판사")
            .coverImgUrl("/cover.jpg").build(), 10L);
        var second = withId(BookOrigin.builder().platform(subscription).platformBookId("b-11")
            .isbn13Raw("9781234567890").titleRaw("구독 도서").titleNorm("구독도서").build(), 11L);
        var duplicate = withId(BookOrigin.builder().platform(library).platformBookId("b-12")
            .isbn13Raw("9781234567890").titleRaw("중복 도서").titleNorm("중복도서").build(), 12L);
        given(books.findAllByIsbn13RawInAndActiveTrueOrderByIdAsc(anyList()))
            .willReturn(List.of(first, second, duplicate));
        given(books.findLibraryPlatformSummaries(anyList())).willReturn(List.of(
            new BookPlatformSummary(10L, 1L, "LIB", "도서관", 4), new BookPlatformSummary(12L, 1L, "LIB", "도서관", 2)));
        given(books.findSubscriptionPlatformSummaries(anyList()))
            .willReturn(List.of(new BookPlatformSummary(11L, 2L, "SUB", "구독", 1)));
        given(client.fetch(eq(RankingType.POPULAR), any())).willReturn(new DataLibraryClient.Result(today, List.of(
            item(1, "9781234567890"), item(2, "9781234567890"), item(3, "9789999999999"), item(4, null))));

        service.refresh(RankingType.POPULAR);
        var response = service.get(RankingType.POPULAR);
        assertThat(response.updatedAt()).isNotNull();
        assertThat(response.sourceDate()).isEqualTo(today);
        assertThat(response.books()).hasSize(4);
        var book = response.books().get(0);
        assertThat(book.bookId()).isEqualTo(10L);
        assertThat(book.title()).isEqualTo("제공 도서1");
        assertThat(book.author()).isEqualTo("제공 저자");
        assertThat(book.thumbnail()).isEqualTo("/source.jpg");
        assertThat(book.platforms()).hasSize(2);
        assertThat(book.platforms().stream().map(RankingResponse.Platform::platformType).toList())
            .isEqualTo(List.of("LIBRARY", "SUBSCRIPTION"));
        assertThat(response.books().get(1).rank()).isEqualTo(2);
        assertThat(response.books().get(2).bookId()).isNull();
        assertThat(response.books().get(2).isbn13()).isEqualTo("9789999999999");
        assertThat(response.books().get(2).title()).isEqualTo("제공 도서3");
        assertThat(response.books().get(2).platforms()).isEmpty();
        assertThat(response.books().get(3).bookId()).isNull();
        assertThat(response.books().get(3).rank()).isEqualTo(4);
        assertThat(response.books().get(3).title()).isEqualTo("제공 도서4");
        then(values).should().set(eq(RankingType.POPULAR.cacheKey()), anyString());
        then(values).should().get(RankingType.POPULAR.cacheKey());
        then(values).shouldHaveNoMoreInteractions();
    }

    @Test
    void retainsAllFiftyBooksWithoutTruncation() {
        var source = new ArrayList<DataLibraryClient.Item>();
        for (int i = 1; i <= 50; i++) source.add(item(i, null));
        given(client.fetch(eq(RankingType.POPULAR), any())).willReturn(new DataLibraryClient.Result(today, source));
        service.refresh(RankingType.POPULAR);
        assertThat(service.get(RankingType.POPULAR).books()).hasSize(50);
        then(books).shouldHaveNoInteractions();
    }

    @Test
    void readsOnlyRedisAndKeepsPreviousSnapshotOnRefreshFailure() {
        String previous = mapper.writeValueAsString(RankingResponse.empty());
        cache.put(RankingType.POPULAR.cacheKey(), previous);
        assertThat(service.get(RankingType.POPULAR).books()).isEmpty();
        then(client).shouldHaveNoInteractions();
        then(books).shouldHaveNoInteractions();
        given(client.fetch(eq(RankingType.POPULAR), any())).willThrow(new IllegalArgumentException("parse failed"));
        service.refresh(RankingType.POPULAR);
        assertThat(cache.get(RankingType.POPULAR.cacheKey())).isEqualTo(previous);
        then(values).should(never()).set(anyString(), anyString());
        then(redis).should(never()).delete(anyString());
    }

    @Test
    void initializesOnlyMissingCacheAndRefreshesListsIndependently() {
        cache.put(RankingType.POPULAR.cacheKey(), mapper.writeValueAsString(RankingResponse.empty()));
        given(client.fetch(eq(RankingType.TRENDING), any()))
            .willReturn(new DataLibraryClient.Result(today, List.of(item(1, null))));
        service.initialize();
        then(client).should(never()).fetch(eq(RankingType.POPULAR), any());
        assertThat(service.get(RankingType.TRENDING).books()).hasSize(1);
        given(client.fetch(eq(RankingType.POPULAR), any())).willThrow(new IllegalStateException("upstream failed"));
        new RankingScheduler(service).refresh();
        then(client).should(times(2)).fetch(eq(RankingType.TRENDING), any());
        then(values).should(never()).set(eq(RankingType.POPULAR.cacheKey()), anyString());
    }

    @Test
    void missingCacheRedisFailureAndAbsentKeyAreSafe() {
        assertThat(service.get(RankingType.POPULAR)).isEqualTo(RankingResponse.empty());
        given(values.get(anyString())).willThrow(new IllegalStateException("Redis unavailable"));
        assertThat(service.get(RankingType.POPULAR)).isEqualTo(RankingResponse.empty());
        given(client.isConfigured()).willReturn(false);
        service.initialize();
        service.refresh(RankingType.POPULAR);
        then(client).should(never()).fetch(any(), any());
        then(books).shouldHaveNoInteractions();
    }

    private DataLibraryClient.Item item(int rank, String isbn) {
        return new DataLibraryClient.Item(rank, isbn, "제공 도서" + rank, "제공 저자", "출판사", "/source.jpg", 20L);
    }
}
