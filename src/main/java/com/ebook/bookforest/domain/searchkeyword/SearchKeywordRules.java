package com.ebook.bookforest.domain.searchkeyword;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class SearchKeywordRules {
    private static final Pattern SPACE = Pattern.compile("[\\p{Z}\\s]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+", Pattern.UNICODE_CHARACTER_CLASS);
    // ponytail: 명확한 한국 전화번호 형식만 제외한다. 국제 번호가 필요하면 국가별 규칙을 추가한다.
    private static final Pattern PHONE = Pattern.compile("(?<![\\p{L}\\p{N}])(?:01[016789]|02|0[3-6][1-5])[- .]?[0-9]{3,4}[- .]?[0-9]{4}(?![\\p{L}\\p{N}])");

    private SearchKeywordRules() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        text = SPACE.matcher(text).replaceAll(" ").replaceAll("[\\p{Cc}\\p{Cf}]", "");
        return SPACE.matcher(text).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }

    public static boolean valid(String keyword) {
        int length = keyword.codePointCount(0, keyword.length());
        return length >= 2 && length <= 100 && keyword.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    public static boolean visible(String keyword, Set<String> blocked) {
        return valid(keyword) && !blocked.contains(keyword)
            && !EMAIL.matcher(keyword).find() && !PHONE.matcher(keyword).find();
    }
}
