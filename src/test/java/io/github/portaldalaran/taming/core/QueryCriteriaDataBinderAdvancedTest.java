package io.github.portaldalaran.taming.core;

import io.github.portaldalaran.taming.pojo.PageCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.JsonUtils;
import io.github.portaldalaran.taming.utils.QueryConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QueryCriteriaDataBinder 的多值、下标、嵌套、保留字段与绑定异常路径。
 */
class QueryCriteriaDataBinderAdvancedTest {

    @Data
    @EqualsAndHashCode(callSuper = true)
    static class UserPageCriteria extends PageCriteria<Object> {
        private String name;
        private Integer age;
        private Long userId;
    }

    private UserPageCriteria bind(MockHttpServletRequest request) {
        UserPageCriteria criteria = new UserPageCriteria();
        new QueryCriteriaDataBinder(criteria, "criteria").bind(request);
        return criteria;
    }

    @Test
    void bind_multiValueParams_keepAllValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("age@in", new String[]{"1", "2", "3"});

        List<QueryCriteriaParam<Object>> params = bind(request).getCriteriaParams();
        assertEquals(1, params.size());
        assertEquals("age", params.get(0).getName());
        assertEquals(QueryConstants.IN, params.get(0).getOperation());
        assertInstanceOf(String[].class, params.get(0).getValue());
        assertEquals(3, ((String[]) params.get(0).getValue()).length);
    }

    @Test
    @SuppressWarnings("unchecked")
    void bind_applySqlIndexedParams_groupIntoOneParam() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("applySql[0]", "date_format(created_at,'%Y-%m-%d') = {0}");
        request.addParameter("applySql[1]", "2008-08-08");

        List<QueryCriteriaParam<Object>> params = bind(request).getCriteriaParams();
        assertEquals(1, params.size());
        assertEquals(QueryConstants.APPLY_SQL, params.get(0).getName());
        assertEquals(QueryConstants.APPLY_SQL, params.get(0).getOperation());
        List<QueryCriteriaParam<?>> children = (List<QueryCriteriaParam<?>>) params.get(0).getValue();
        assertEquals(2, children.size());
        assertEquals("0", children.get(0).getName());
        assertEquals("1", children.get(1).getName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void bind_andNestedPrefixes_createGroups() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("and[name]", "wang");
        request.addParameter("nested[age]", "18");

        List<QueryCriteriaParam<Object>> params = bind(request).getCriteriaParams();
        assertEquals(2, params.size());
        assertTrue(params.stream().anyMatch(p -> QueryConstants.AND.equalsIgnoreCase(p.getName())
                && p.getValue() instanceof List));
        assertTrue(params.stream().anyMatch(p -> QueryConstants.NESTED.equalsIgnoreCase(p.getName())
                && p.getValue() instanceof List));
        List<QueryCriteriaParam<?>> children =
                (List<QueryCriteriaParam<?>>) params.stream()
                        .filter(p -> QueryConstants.AND.equalsIgnoreCase(p.getName()))
                        .findFirst().orElseThrow().getValue();
        assertEquals("name", children.get(0).getName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void bind_jsonOrValue_expandsToChildCriteria() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("or", "{\"id\":2,\"age\":18}");

        List<QueryCriteriaParam<Object>> params = bind(request).getCriteriaParams();
        assertEquals(1, params.size());
        assertEquals(QueryConstants.OR, params.get(0).getName());
        List<QueryCriteriaParam<?>> children = (List<QueryCriteriaParam<?>>) params.get(0).getValue();
        assertEquals(2, children.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void bind_malformedJsonWithBrace_fallsBackToLiteral() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("or", "not-json {oops");

        List<QueryCriteriaParam<Object>> params = bind(request).getCriteriaParams();
        assertEquals(1, params.size());
        List<QueryCriteriaParam<?>> children = (List<QueryCriteriaParam<?>>) params.get(0).getValue();
        assertEquals(1, children.size());
        assertEquals("not-json {oops", children.get(0).getValue());
    }

    @Test
    void bind_reservedFields_bindToBeanNotToWhereConditions() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("pageNo", "2");
        request.addParameter("pageSize", "20");
        request.addParameter("orderBy", "name@desc");
        request.addParameter("fields", "id,name");
        request.addParameter("groupBy", "name");
        request.addParameter("having", "count(id)>1");

        UserPageCriteria criteria = bind(request);
        assertTrue(criteria.getCriteriaParams().isEmpty());
        assertEquals(2, criteria.getPageNo());
        assertEquals(20, criteria.getPageSize());
        assertEquals("name@desc", criteria.getOrderBy());
        assertEquals("id,name", criteria.getFields());
        assertEquals("name", criteria.getGroupBy());
        assertEquals("count(id)>1", criteria.getHaving());
        assertEquals(2, criteria.getPageParam().getPageNo());
        assertEquals(20, criteria.getPageParam().getPageSize());
    }

    @Test
    void bind_uriTemplateVariables_areMerged() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("userId", "7"));
        request.addParameter("name", "wang");

        UserPageCriteria criteria = bind(request);
        assertEquals(7L, criteria.getUserId());
        assertEquals(1, criteria.getCriteriaParams().size());
    }

    @Test
    void bind_fieldsWithOnlyRelationFields_rewritesToEmptyMainFields() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("fields", "user.name,user.sex");

        UserPageCriteria criteria = bind(request);
        assertEquals("", criteria.getFields());
        assertNotNull(criteria.getSelectAssociationFields());
        assertEquals(1, criteria.getSelectAssociationFields().size());
        assertEquals(List.of("name", "sex"),
                criteria.getSelectAssociationFields().get(0).getFieldNames());
    }

    @Test
    void bind_dottedParams_notAddedToWhereConditions() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("user.sex", "male");
        request.addParameter("name", "wang");

        UserPageCriteria criteria = bind(request);
        assertEquals(1, criteria.getCriteriaParams().size());
        assertEquals("name", criteria.getCriteriaParams().get(0).getName());
    }

    @Test
    void closeNoCatch_throwsOnBindingErrors() {
        UserPageCriteria criteria = new UserPageCriteria();
        QueryCriteriaDataBinder binder = new QueryCriteriaDataBinder(criteria, "criteria");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("age", "not-a-number");
        binder.bind(request);

        assertTrue(binder.getBindingResult().hasErrors());
        assertThrows(ServletRequestBindingException.class, binder::closeNoCatch);

        QueryCriteriaDataBinder okBinder = new QueryCriteriaDataBinder(new UserPageCriteria(), "criteria");
        okBinder.bind(new MockHttpServletRequest());
        assertDoesNotThrow(okBinder::closeNoCatch);
    }

    @Test
    void bind_secondAtSign_staysInOperation() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("name@like@extra", "wang");
        List<QueryCriteriaParam<Object>> params = bind(request).getCriteriaParams();
        assertEquals("name", params.get(0).getName());
        assertEquals("like@extra", params.get(0).getOperation());
    }

    @Test
    void bind_plainValue_bindsBeanPropertyAndCriteria() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("name", "wang");
        UserPageCriteria criteria = bind(request);
        assertEquals("wang", criteria.getName());
        assertEquals(1, criteria.getCriteriaParams().size());
        // 无 @ 算子时 operation 为空，由 WhereParamsBuilder 按默认等值处理
        assertTrue(criteria.getCriteriaParams().get(0).getOperation() == null
                || criteria.getCriteriaParams().get(0).getOperation().isEmpty());
    }

    @Test
    void jsonUtils_roundTripUsedByBinder() {
        String json = JsonUtils.toJsonString(Map.of("id", 2, "age", 18));
        assertNotNull(json);
        assertEquals(2, ((Map<?, ?>) JsonUtils.parseObject(json, Map.class)).get("id"));
    }
}
