package com.ebook.bookforest.domain.ranking.service;

import com.ebook.bookforest.domain.book.dto.BookAvailabilityInfo;
import com.ebook.bookforest.domain.book.service.BookService;
import com.ebook.bookforest.domain.ranking.client.DataLibraryClient;
import com.ebook.bookforest.domain.ranking.dto.RankingResponse;
import com.ebook.bookforest.domain.ranking.enums.RankingType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

/**
 * 도서관 정보나루 도서 순위 서비스
 * 외부 API는 스케줄러에서만 호출하고, 조회 API는 Redis에 저장한 결과만 읽음
 * 갱신에 실패해도 이전 순위를 그대로 유지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final DataLibraryClient dataLibraryClient;
    private final BookService bookService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** 저장된 순위 조회, 없거나 읽기에 실패하면 빈 응답 */
    public RankingResponse get(RankingType type) {
        try {
            String cached = redis.opsForValue().get(type.cacheKey());
            return cached == null ? RankingResponse.empty() : objectMapper.readValue(cached, RankingResponse.class);
        } catch (RuntimeException exception) {
            logFailure("조회", type, exception);
            return RankingResponse.empty();
        }
    }

    /** 저장된 순위가 없는 종류만 갱신, API 키가 없으면 갱신하지 않음 */
    public void initialize() {
        if (!dataLibraryClient.isConfigured()) {
            log.warn("도서관 정보나루 API 키가 없어 도서 순위 갱신 비활성");
            return;
        }

        for (RankingType type : RankingType.values()) {
            try {
                if (!redis.hasKey(type.cacheKey())) {
                    refresh(type);
                }
            } catch (RuntimeException exception) {
                logFailure("초기화", type, exception);
            }
        }
    }

    /** 외부 API에서 순위를 받아 서비스 도서와 연결한 뒤 저장 */
    public void refresh(RankingType type) {
        if (!dataLibraryClient.isConfigured()) {
            return;
        }

        try {
            DataLibraryClient.Result source = dataLibraryClient.fetch(type, LocalDate.now(SEOUL));
            RankingResponse response = new RankingResponse(
                OffsetDateTime.now(SEOUL),
                source.sourceDate(),
                toRankingBooks(source.books())
            );
            redis.opsForValue().set(type.cacheKey(), objectMapper.writeValueAsString(response));
        } catch (RuntimeException exception) {
            logFailure("갱신", type, exception);
        }
    }

    /** 순위 도서를 ISBN이 같은 서비스 도서와 연결, 여러 판이 있으면 제공 플랫폼을 합침 */
    private List<RankingResponse.Book> toRankingBooks(List<DataLibraryClient.Item> items) {
        List<String> isbns = items.stream()
            .map(DataLibraryClient.Item::isbn13)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<String, List<BookAvailabilityInfo>> editionsByIsbn = bookService.findAvailabilitiesByIsbns(isbns)
            .stream()
            .collect(Collectors.groupingBy(BookAvailabilityInfo::isbn13));

        List<RankingResponse.Book> books = new ArrayList<>();
        for (DataLibraryClient.Item item : items) {
            List<BookAvailabilityInfo> editions = editionsByIsbn.getOrDefault(item.isbn13(), List.of());
            books.add(new RankingResponse.Book(
                item.rank(),
                editions.isEmpty() ? null : editions.get(0).bookOriginId(),
                item.isbn13(),
                item.title(),
                item.author(),
                item.publisher(),
                item.thumbnail(),
                item.loanCount(),
                mergePlatforms(editions)
            ));
        }
        return List.copyOf(books);
    }

    private List<RankingResponse.Platform> mergePlatforms(List<BookAvailabilityInfo> editions) {
        Map<String, RankingResponse.Platform> platformsByKey = new LinkedHashMap<>();
        for (BookAvailabilityInfo edition : editions) {
            for (BookAvailabilityInfo.Platform platform : edition.platforms()) {
                String type = platform.platformType().name();
                platformsByKey.putIfAbsent(
                    type + ":" + platform.platformId(),
                    new RankingResponse.Platform(platform.platformId(), platform.platformName(), type)
                );
            }
        }
        return List.copyOf(platformsByKey.values());
    }

    /** API 키가 로그에 남지 않도록 예외 메시지 대신 예외 종류와 HTTP 상태만 기록 */
    private void logFailure(String operation, RankingType type, RuntimeException exception) {
        String httpStatus = exception instanceof RestClientResponseException http
            ? String.valueOf(http.getStatusCode().value())
            : "-";
        log.warn(
            "도서 순위 {} 실패: type={}, exceptionType={}, httpStatus={}",
            operation,
            type,
            exception.getClass().getSimpleName(),
            httpStatus
        );
    }
}
