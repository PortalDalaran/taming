package io.github.portaldalaran.taming.pojo;

import io.github.portaldalaran.taming.support.TestEntities;
import io.github.portaldalaran.taming.utils.QueryConstants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * POJO 基类、分页对象与查询条件对象的完整行为。
 */
class PojoTest {

    static class SampleCriteria extends QueryCriteria<TestEntities.UserDO> {
    }

    static class SamplePageCriteria extends PageCriteria<TestEntities.UserDO> {
    }

    @Test
    void pageCriteria_hasDefaultPageValues() {
        SamplePageCriteria criteria = new SamplePageCriteria();
        assertEquals(1, criteria.getPageNo());
        assertEquals(15, criteria.getPageSize());
        assertEquals(1, criteria.getPageParam().getPageNo());
        assertEquals(15, criteria.getPageParam().getPageSize());
    }

    @Test
    void pageCriteria_customValues_flowIntoPageParam() {
        SamplePageCriteria criteria = new SamplePageCriteria();
        criteria.setPageNo(3);
        criteria.setPageSize(50);
        assertEquals(3, criteria.getPageParam().getPageNo());
        assertEquals(50, criteria.getPageParam().getPageSize());
    }

    @Test
    void pageParam_constructorsAndDefaults() {
        PageParam defaults = new PageParam();
        assertEquals(1, defaults.getPageNo());
        assertEquals(10, defaults.getPageSize());

        PageParam custom = new PageParam(2, 20);
        assertEquals(2, custom.getPageNo());
        assertEquals(20, custom.getPageSize());
        custom.setPageNo(5);
        custom.setPageSize(100);
        assertEquals(5, custom.getPageNo());
        assertEquals(100, custom.getPageSize());
    }

    @Test
    void queryCriteriaParam_valuesAccessors() {
        QueryCriteriaParam<TestEntities.UserDO> param =
                new QueryCriteriaParam<>("age", QueryConstants.BETWEEN, 18, 30);
        assertEquals("age", param.getName());
        assertEquals(QueryConstants.BETWEEN, param.getOperation());
        assertArrayEquals(new Object[]{18, 30}, param.getValues());
        assertEquals(18, param.getValue());
        assertEquals(30, param.getValue2());

        param.setValue(20, 40);
        assertEquals(20, param.getValue());
        assertEquals(40, param.getValue2());

        QueryCriteriaParam<TestEntities.UserDO> single =
                new QueryCriteriaParam<>("name", QueryConstants.EQ, "wang");
        assertNull(single.getValue2());

        QueryCriteriaParam<TestEntities.UserDO> empty = new QueryCriteriaParam<>();
        assertNull(empty.getValue());
        assertNull(empty.getValue2());
        empty.setName("name");
        empty.setOperation(QueryConstants.EQ);
        empty.setValues(new Object[]{"wang"});
        assertEquals("wang", empty.getValue());
    }

    @Test
    void queryCriteriaParam_lambdaConstructorResolvesFieldName() {
        QueryCriteriaParam<TestEntities.UserDO> param =
                new QueryCriteriaParam<>(TestEntities.UserDO::getName, QueryConstants.LIKE, "wang");
        assertEquals("name", param.getName());
        assertNotNull(param.getColumn());
        assertEquals(QueryConstants.LIKE, param.getOperation());
    }

    @Test
    void selectAssociationFields_constructorsAndSetters() {
        SelectAssociationFields fields = new SelectAssociationFields("user", List.of("name", "sex"));
        assertEquals("user", fields.getEntityName());
        assertEquals(List.of("name", "sex"), fields.getFieldNames());

        SelectAssociationFields empty = new SelectAssociationFields();
        assertNull(empty.getEntityName());
        empty.setEntityName("dept");
        empty.setFieldNames(List.of("deptName"));
        assertEquals("dept", empty.getEntityName());
        assertEquals(1, empty.getFieldNames().size());
    }

    @Test
    void queryCriteria_addAndCleanHelpers() {
        SampleCriteria criteria = new SampleCriteria();
        criteria.addSelectAssField("user", List.of("name"));
        criteria.addSelectAssField("dept", List.of("deptName"));
        assertEquals(2, criteria.getSelectAssociationFields().size());

        criteria.addQueryCriteriaParam("name", QueryConstants.EQ, "wang");
        criteria.addQueryCriteriaParam("age", QueryConstants.GT, 18);
        criteria.addQueryCriteriaParam(TestEntities.UserDO::getName, QueryConstants.LIKE, "wa");
        assertEquals(3, criteria.getCriteriaParams().size());

        criteria.cleanQueryCriteriaParam("AGE");
        assertEquals(2, criteria.getCriteriaParams().size());
        criteria.cleanQueryCriteriaParam(TestEntities.UserDO::getName);
        // 两个 name 条件（字符串与 lambda 添加）按名字一并移除，只剩 0 条
        assertEquals(0, criteria.getCriteriaParams().size());

        criteria.cleanQueryCriteriaParam((String) null);
        assertEquals(0, criteria.getCriteriaParams().size());
    }

    @Test
    void queryCriteria_criteriaParamsCanBeNull() {
        SampleCriteria criteria = new SampleCriteria();
        assertNull(criteria.getCriteriaParams());
        assertDoesNotThrow(() -> criteria.cleanQueryCriteriaParam("name"));
        criteria.addQueryCriteriaParam("name", QueryConstants.EQ, "wang");
        assertTrue(criteria.getCriteriaParams().size() == 1);
        criteria.setCriteriaParams(null);
        assertNull(criteria.getCriteriaParams());
    }
}
