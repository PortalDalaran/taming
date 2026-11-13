package io.github.portaldalaran.taming.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlUtilsTest {

    @Test
    void checkSqlInjection_detectsClassicPayloads() {
        assertTrue(SqlUtils.checkSqlInjection("1=1'"));
        assertTrue(SqlUtils.checkSqlInjection("name';--"));
        assertTrue(SqlUtils.checkSqlInjection("id;drop"));
        assertTrue(SqlUtils.checkSqlInjection("a' or '1'='1"));
    }

    @Test
    void checkSqlInjection_allowsSafeIdentifiersAndClauses() {
        assertFalse(SqlUtils.checkSqlInjection("name"));
        assertFalse(SqlUtils.checkSqlInjection("id@desc,name@asc"));
        assertFalse(SqlUtils.checkSqlInjection("id,name,age"));
        assertFalse(SqlUtils.checkSqlInjection("count(age)"));
        assertFalse(SqlUtils.checkSqlInjection("sum(age)>18"));
    }

    @Test
    void apostrophe_rejectedByIdentifierCheck_allowedByFragmentCheck() {
        // 字段名级规则会拒绝引号；SQL 片段规则允许引号（由调用方决定是否扫描 value）
        assertTrue(SqlUtils.checkSqlInjection("O'Brien"));
        assertFalse(SqlUtils.checkSqlFragment("O'Brien"));
    }

    @Test
    void checkSqlFragment_blocksStackedAndComments_allowsQuotedSql() {
        assertTrue(SqlUtils.checkSqlFragment("1=1; select * from user"));
        assertTrue(SqlUtils.checkSqlFragment("id > 0 -- comment"));
        assertTrue(SqlUtils.checkSqlFragment("id > 0 /*x*/"));
        assertTrue(SqlUtils.checkSqlFragment("1 union select password from user"));

        assertFalse(SqlUtils.checkSqlFragment("date_format(dateColumn,'%Y-%m-%d') = {0}"));
        assertFalse(SqlUtils.checkSqlFragment("select id from table where id < 3"));
        // having 合法片段：引号字面量不应被片段校验误杀
        assertFalse(SqlUtils.checkSqlFragment("date_format(created,'%Y')>2020"));
        assertFalse(SqlUtils.checkSqlFragment("sum(age)>18,count(id)>1"));
    }
}
