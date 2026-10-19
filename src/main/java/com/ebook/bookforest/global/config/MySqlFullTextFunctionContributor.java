package com.ebook.bookforest.global.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * MySQL FULLTEXT 검색 함수의 HQL(Hibernate Query Language) 등록
 * HQL에서 full_text_match(컬럼1, 컬럼2, 컬럼3, 불리언 모드 검색어) 형식으로 사용
 * 스프링 빈이 아니라 META-INF/services 파일로 Hibernate가 직접 불러옴
 */
public class MySqlFullTextFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        contributions.getFunctionRegistry().registerPattern(
            "full_text_match",
            "match(?1, ?2, ?3) against (?4 in boolean mode) > 0",
            contributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.BOOLEAN)
        );
    }
}
