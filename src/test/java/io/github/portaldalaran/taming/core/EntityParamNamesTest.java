package io.github.portaldalaran.taming.core;

import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.QueryConstants;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 参数名前缀/后缀识别规则。
 */
class EntityParamNamesTest {

    @Test
    void groupOperatorPrefixes_areRecognizedCaseInsensitively() {
        assertTrue(new EntityParamNames("or", "id").isOperatorByPrefixParamName());
        assertTrue(new EntityParamNames("AND", "id").isOperatorByPrefixParamName());
        assertTrue(new EntityParamNames("NeStEd", "id").isOperatorByPrefixParamName());
        assertFalse(new EntityParamNames("applySql", "0").isOperatorByPrefixParamName());
        assertFalse(new EntityParamNames("", "id").isOperatorByPrefixParamName());
    }

    @Test
    void groupOperatorNames_areRecognized() {
        assertTrue(new EntityParamNames("", "or").isOperatorByParamName());
        assertTrue(new EntityParamNames("", "AND").isOperatorByParamName());
        assertTrue(new EntityParamNames("", "nested").isOperatorByParamName());
        assertFalse(new EntityParamNames("", "name").isOperatorByParamName());
    }

    @Test
    void applySqlPrefix_isRecognizedCaseInsensitively() {
        assertTrue(new EntityParamNames("applySql", "0").isApplySqlOperator());
        assertTrue(new EntityParamNames("APPLYSQL", "1").isApplySqlOperator());
        assertFalse(new EntityParamNames("or", "0").isApplySqlOperator());
    }

    @Test
    void reservedFields_areNotUserDefined() {
        String[] reserved = {QueryConstants.FIELDS, QueryConstants.GROUP_BY,
                QueryConstants.ORDER_BY, QueryConstants.HAVING,
                QueryConstants.PAGE_NO, QueryConstants.PAGE_SIZE};
        for (String field : reserved) {
            assertFalse(new EntityParamNames("", field).isUserDefinedField(), field);
            assertFalse(new EntityParamNames("", field.toUpperCase()).isUserDefinedField(), field);
        }
        assertTrue(new EntityParamNames("", "name").isUserDefinedField());
    }

    @Test
    void numericIndex_detection() {
        assertTrue(new EntityParamNames("applySql", "0").isNumber());
        assertTrue(new EntityParamNames("", "12345").isNumber());
        assertFalse(new EntityParamNames("", "1a").isNumber());
        assertFalse(new EntityParamNames("", "").isNumber());
        assertFalse(new EntityParamNames("", null).isNumber());
    }

    @Test
    void findQueryCriteria_searchesByPrefixAndNameIgnoringCase() {
        List<QueryCriteriaParam<?>> params = new ArrayList<>();
        params.add(new QueryCriteriaParam<>((String) null, null, "x"));
        QueryCriteriaParam<Object> or = new QueryCriteriaParam<>("OR", null, "x");
        QueryCriteriaParam<Object> name = new QueryCriteriaParam<>("name", null, "x");
        params.add(or);
        params.add(name);

        assertSame(or, new EntityParamNames("or", "id").findQueryCriteriaByPrefixParamName(params));
        assertSame(name, new EntityParamNames("", "NAME").findQueryCriteriaByParamName(params));
        assertNull(new EntityParamNames("missing", "x").findQueryCriteriaByPrefixParamName(params));
        assertNull(new EntityParamNames("", "missing").findQueryCriteriaByParamName(params));
    }

    @Test
    void accessors_areMutable() {
        EntityParamNames names = new EntityParamNames("or", "id");
        assertEquals("or", names.getPrefixParamName());
        assertEquals("id", names.getParamName());
        names.setParamName("age");
        names.setPrefixParamName("and");
        assertEquals("age", names.getParamName());
        assertEquals("and", names.getPrefixParamName());
    }
}
