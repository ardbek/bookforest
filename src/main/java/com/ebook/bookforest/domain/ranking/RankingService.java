package com.ebook.bookforest.domain.ranking;

import com.ebook.bookforest.domain.book.entity.BookOrigin;
import com.ebook.bookforest.domain.book.repository.BookPlatformSummary;
import com.ebook.bookforest.domain.book.repository.BookRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final DataLibraryClient client;
    private final BookRepository repository;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RankingResponse get(RankingType type) {
        try {
            String cached = redis.opsForValue().get(type.cacheKey());
            return cached == null ? RankingResponse.empty() : mapper.readValue(cached, RankingResponse.class);
        } catch (RuntimeException exception) {
            logFailure("read", type, exception);
            return RankingResponse.empty();
        }
    }

    public void initialize() {
        if (!client.isConfigured()) {
            log.warn("Ranking refresh disabled: DATA4LIBRARY_API_KEY is not configured");
            return;
        }
        for (RankingType type : RankingType.values()) {
            try {
                if (!redis.hasKey(type.cacheKey())) refresh(type);
            } catch (RuntimeException exception) {
                logFailure("initialize", type, exception);
            }
        }
    }

    public void refresh(RankingType type) {
        if (!client.isConfigured()) return;
        try {
            var source = client.fetch(type, LocalDate.now(SEOUL));
            var response = new RankingResponse(OffsetDateTime.now(SEOUL), source.sourceDate(), enrich(source.books()));
            redis.opsForValue().set(type.cacheKey(), mapper.writeValueAsString(response));
        } catch (RuntimeException exception) {
            logFailure("refresh", type, exception);
        }
    }

    private List<RankingResponse.Book> enrich(List<DataLibraryClient.Item> source) {
        var isbns = source.stream().map(DataLibraryClient.Item::isbn13).filter(Objects::nonNull).distinct().toList();
        List<BookOrigin> matches = isbns.isEmpty() ? List.of()
            : repository.findAllByIsbn13RawInAndActiveTrueOrderByIdAsc(isbns);
        var byIsbn = matches.stream().collect(Collectors.groupingBy(BookOrigin::getIsbn13Raw));
        var ids = matches.stream().map(BookOrigin::getId).toList();
        Map<Long, List<RankingResponse.Platform>> platforms = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            addPlatforms(platforms, repository.findLibraryPlatformSummaries(ids), "LIBRARY");
            addPlatforms(platforms, repository.findSubscriptionPlatformSummaries(ids), "SUBSCRIPTION");
        }
        var result = new ArrayList<RankingResponse.Book>();
        for (var item : source) {
            List<BookOrigin> editions = byIsbn.getOrDefault(item.isbn13(), List.of());
            var available = new LinkedHashMap<String, RankingResponse.Platform>();
            for (BookOrigin edition : editions) {
                for (var platform : platforms.getOrDefault(edition.getId(), List.of())) {
                    available.putIfAbsent(platform.platformType() + ":" + platform.platformId(), platform);
                }
            }
            result.add(new RankingResponse.Book(item.rank(), editions.isEmpty() ? null : editions.get(0).getId(),
                item.isbn13(), item.title(), item.author(), item.publisher(), item.thumbnail(), item.loanCount(),
                List.copyOf(available.values())));
        }
        return List.copyOf(result);
    }

    private void addPlatforms(Map<Long, List<RankingResponse.Platform>> target,
                             List<BookPlatformSummary> summaries, String type) {
        for (var summary : summaries) {
            target.computeIfAbsent(summary.bookOriginId(), ignored -> new ArrayList<>())
                .add(new RankingResponse.Platform(summary.platformId(), summary.platformName(), type));
        }
    }

    private void logFailure(String operation, RankingType type, RuntimeException exception) {
        log.warn("Ranking {} failed for {}: {}{}", operation, type, exception.getClass().getSimpleName(),
            exception instanceof RestClientResponseException http ? " HTTP " + http.getStatusCode().value() : "");
    }
}
