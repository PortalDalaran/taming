package io.github.portaldalaran.taming.core;

import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class QueryCriteriaDataBinderTest {

    @Data
    @EqualsAndHashCode(callSuper = true)
    static class UserCriteria extends QueryCriteria<Object> {
        private String name;
        private Integer age;
    }

    @Test
    @SuppressWarnings("unchecked")
    void bind_parsesOperationAndOrGroup() {
        UserCriteria criteria = new UserCriteria();
        QueryCriteriaDataBinder binder = new QueryCriteriaDataBinder(criteria, "criteria");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("name@like", "wang");
        request.addParameter("age@ge", "18");
        request.addParameter("or[id]", "2");

        binder.bind(request);

        assertNotNull(criteria.getCriteriaParams());
        List<QueryCriteriaParam<?>> params = (List<QueryCriteriaParam<?>>) (List<?>) criteria.getCriteriaParams();
        assertEquals(3, params.size());

        assertEquals("name", params.get(0).getName());
        assertEquals("like", params.get(0).getOperation());
        assertEquals("wang", params.get(0).getValue());

        assertEquals("age", params.get(1).getName());
        assertEquals("ge", params.get(1).getOperation());

        assertEquals("or", params.get(2).getName());
        assertInstanceOf(List.class, params.get(2).getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void bind_splitsOnlyFirstAt() {
        UserCriteria criteria = new UserCriteria();
        QueryCriteriaDataBinder binder = new QueryCriteriaDataBinder(criteria, "criteria");

        MockHttpServletRequest request = new MockHttpServletRequest();
        // 第二个 @ 应留在 operation 内，而不是再拆字段名
        request.addParameter("name@like", "wang");

        binder.bind(request);

        List<QueryCriteriaParam<?>> params = (List<QueryCriteriaParam<?>>) (List<?>) criteria.getCriteriaParams();
        assertEquals(1, params.size());
        assertEquals("name", params.get(0).getName());
        assertEquals("like", params.get(0).getOperation());
    }
}
