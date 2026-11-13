package io.github.portaldalaran.taming.builder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupParamsBuilderTest {

    @Test
    void splitHavingClauses_keepsCommasInsideParens() {
        List<String> parts = GroupParamsBuilder.splitHavingClauses("sum(age)>18,age in (1,2,3),count(id)>1");
        assertEquals(3, parts.size());
        assertEquals("sum(age)>18", parts.get(0));
        assertEquals("age in (1,2,3)", parts.get(1));
        assertEquals("count(id)>1", parts.get(2));
    }

    @Test
    void splitHavingClauses_singleClause() {
        List<String> parts = GroupParamsBuilder.splitHavingClauses("date_format(created,'%Y')>2020");
        assertEquals(1, parts.size());
        assertEquals("date_format(created,'%Y')>2020", parts.get(0));
    }

    @Test
    void extractFieldToken_handlesFormatArgsAndNestedFuncs() {
        assertEquals("created", GroupParamsBuilder.extractFieldToken("created,'%Y'"));
        assertEquals("age", GroupParamsBuilder.extractFieldToken("age"));
        assertEquals("age", GroupParamsBuilder.extractFieldToken("ifnull(age,0)"));
        assertEquals("age", GroupParamsBuilder.extractFieldToken("ifnull(age,0)"));
    }

    @Test
    void findMatchingParen_skipsQuotedParens() {
        String s = "date_format(created,'%Y(m)')";
        int left = s.indexOf('(');
        assertEquals(s.lastIndexOf(')'), GroupParamsBuilder.findMatchingParen(s, left));
    }
}
