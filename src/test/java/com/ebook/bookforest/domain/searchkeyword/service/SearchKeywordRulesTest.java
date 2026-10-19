package com.ebook.bookforest.domain.searchkeyword.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;


class SearchKeywordRulesTest {

    @Test
    void normalizesWithoutTruncatingAndDoesNotBlockIsbn() {
        assertThat(SearchKeywordRules.normalize("  Ｈarry\t\n Potter\u0000  ")).isEqualTo("harry potter");
        assertThat(SearchKeywordRules.isValid("가")).isFalse();
        assertThat(SearchKeywordRules.isValid("!?!")).isFalse();
        assertThat(SearchKeywordRules.isValid("가".repeat(101))).isFalse();
        assertThat(SearchKeywordRules.isVisible("9781234567890", Set.of())).isTrue();
        assertThat(SearchKeywordRules.isVisible("전화 010-1234-5678", Set.of())).isFalse();
        assertThat(SearchKeywordRules.isVisible("a@example.com", Set.of())).isFalse();
        assertThat(SearchKeywordRules.isVisible("금지된 사랑", Set.of("사랑"))).isTrue();
    }
}
