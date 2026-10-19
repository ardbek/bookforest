package com.ebook.bookforest.domain.searchkeyword.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 검색어 정규화와 집계 대상 판단 규칙
 * 도서 검색 쿼리도 같은 정규화를 쓰므로 바꾸면 검색 결과와 인기 검색어 집계가 함께 바뀜
 */
public final class SearchKeywordRules {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 100;
    private static final Pattern SPACE = Pattern.compile("[\\p{Z}\\s]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}\\p{Cf}]");
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+", Pattern.UNICODE_CHARACTER_CLASS);
    /** 명확한 국내 전화번호 형식만 제외, 국제 번호가 필요하면 국가별 규칙 추가 */
    private static final Pattern PHONE = Pattern.compile(
        "(?<![\\p{L}\\p{N}])(?:01[016789]|02|0[3-6][1-5])[- .]?[0-9]{3,4}[- .]?[0-9]{4}(?![\\p{L}\\p{N}])"
    );

    private SearchKeywordRules() {
    }

    /** 검색어 정규화(유니코드 NFKC, 공백 하나로 축약, 제어 문자 제거, 소문자), null이면 빈 문자열 */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }

        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        text = CONTROL.matcher(SPACE.matcher(text).replaceAll(" ")).replaceAll("");
        return SPACE.matcher(text).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }

    /** 집계 가능 여부(2~100자, 문자나 숫자 포함), 정규화한 검색어 기준 */
    public static boolean isValid(String keyword) {
        int length = keyword.codePointCount(0, keyword.length());
        return length >= MIN_LENGTH
            && length <= MAX_LENGTH
            && keyword.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    /** 인기 검색어 노출 가능 여부, 차단어와 이메일, 전화번호가 들어간 검색어 제외 */
    public static boolean isVisible(String keyword, Set<String> blockedKeywords) {
        return isValid(keyword)
            && !blockedKeywords.contains(keyword)
            && !EMAIL.matcher(keyword).find()
            && !PHONE.matcher(keyword).find();
    }
}
