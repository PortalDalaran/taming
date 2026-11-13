package io.github.portaldalaran.taming.pojo;

import io.github.portaldalaran.taming.utils.QueryConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryCriteriaTest {

    static class SampleCriteria extends QueryCriteria<Object> {
    }

    @Test
    void cleanQueryCriteriaParam_nullList_doesNotThrow() {
        SampleCriteria criteria = new SampleCriteria();
        assertDoesNotThrow(() -> criteria.cleanQueryCriteriaParam("name"));
    }

    @Test
    void cleanQueryCriteriaParam_removesMatching() {
        SampleCriteria criteria = new SampleCriteria();
        criteria.addQueryCriteriaParam("name", QueryConstants.EQ, "a");
        criteria.addQueryCriteriaParam("age", QueryConstants.GT, 18);
        criteria.cleanQueryCriteriaParam("name");
        assertEquals(1, criteria.getCriteriaParams().size());
        assertEquals("age", criteria.getCriteriaParams().get(0).getName());
    }
}
