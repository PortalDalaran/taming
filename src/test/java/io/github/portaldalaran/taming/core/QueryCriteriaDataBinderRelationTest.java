package io.github.portaldalaran.taming.core;

import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.pojo.SelectAssociationFields;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryCriteriaDataBinderRelationTest {

    @Data
    @EqualsAndHashCode(callSuper = true)
    static class UserCriteria extends QueryCriteria<Object> {
        private String name;
        private Integer age;
    }

    @Test
    @SuppressWarnings("unchecked")
    void bind_dottedParam_goesToAssociationNotWhere() {
        UserCriteria criteria = new UserCriteria();
        QueryCriteriaDataBinder binder = new QueryCriteriaDataBinder(criteria, "criteria");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("name@like", "wang");
        request.addParameter("user.sex", "1");
        request.addParameter("fields", "id,user.name,age");

        binder.bind(request);

        assertEquals("id,age", criteria.getFields());
        assertNotNull(criteria.getSelectAssociationFields());
        assertEquals(1, criteria.getSelectAssociationFields().size());
        SelectAssociationFields assoc = criteria.getSelectAssociationFields().get(0);
        assertEquals("user", assoc.getEntityName());
        assertTrue(assoc.getFieldNames().contains("sex"));
        assertTrue(assoc.getFieldNames().contains("name"));

        List<QueryCriteriaParam<?>> params = (List<QueryCriteriaParam<?>>) (List<?>) criteria.getCriteriaParams();
        assertEquals(1, params.size());
        assertEquals("name", params.get(0).getName());
    }
}
