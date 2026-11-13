package io.github.portaldalaran.taming.utils;

import io.github.portaldalaran.taming.core.QueryCriteriaException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuildUtilsSqlCheckTest {

    @Test
    void parameterizedValue_withApostrophe_isAllowed() {
        assertDoesNotThrow(() ->
                BuildUtils.checkSqlInjection("name", QueryConstants.LIKE, "O'Brien"));
    }

    @Test
    void applySql_withStackedQuery_isRejected() {
        assertThrows(QueryCriteriaException.class, () ->
                BuildUtils.checkSqlInjection("applySql", QueryConstants.APPLY_SQL,
                        "1=1; select * from user"));
    }

    @Test
    void applySql_withQuotedTemplate_isAllowed() {
        assertDoesNotThrow(() ->
                BuildUtils.checkSqlInjection("applySql", QueryConstants.APPLY_SQL,
                        "date_format(dateColumn,'%Y-%m-%d') = {0}", "2008-08-08"));
    }

    @Test
    void paramName_withInjection_isRejected() {
        assertThrows(QueryCriteriaException.class, () ->
                BuildUtils.checkSqlInjection("name';--", QueryConstants.EQ, "x"));
    }
}
