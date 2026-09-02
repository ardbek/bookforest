package com.ebook.bookforest.global.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * MySQL FULLTEXT 검색 함수를 HQL(Hibernate Query Language)에서 사용할 수 있도록 등록
 */
public class MySqlFullTextFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        contributions.getFunctionRegistry().registerPattern(
            "full_text_match",
            "match(?1, ?2, ?3) against (?4 in boolean mode) > 0",
            contributions.getTypeConfiguration().getBasicTypeRegistry()
                .resolve(StandardBasicTypes.BOOLEAN)
        );
    }
}
