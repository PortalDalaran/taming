package io.github.portaldalaran.taming.builder;

import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.pojo.PageCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.QueryConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriteriaParamsBinderTest {

    @Data
    @EqualsAndHashCode(callSuper = true)
    static class UserCriteria extends QueryCriteria<Object> {
        private String name;
        private Integer age;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    static class UserPageCriteria extends PageCriteria<Object> {
        private String name;
    }

    @Test
    void assemble_withNullOperation_doesNotThrow() {
        UserCriteria criteria = new UserCriteria();
        criteria.setName("david");
        List<QueryCriteriaParam<Object>> params = new ArrayList<>();
        params.add(new QueryCriteriaParam<>("age", null, 18));
        criteria.setCriteriaParams(params);

        BuildHelper<Object> helper = new BuildHelper<>(Object.class, new ArrayList<>());
        helper.init(criteria);

        assertDoesNotThrow(() -> new CriteriaParamsBinder<>(criteria, helper).assembleCriteriaParamsByEntityValue());
        assertEquals(2, helper.getQueryCriteriaParams().size());
    }

    /**
     * PageCriteria 上 pageNo/pageSize 有默认值，组装条件时必须跳过，否则 WHERE 会按列解析失败。
     */
    @Test
    void assemble_skipsPageNoAndPageSize() {
        UserPageCriteria criteria = new UserPageCriteria();
        criteria.setName("room");
        criteria.setPageNo(1);
        criteria.setPageSize(10);

        BuildHelper<Object> helper = new BuildHelper<>(Object.class, new ArrayList<>());
        helper.init(criteria);

        new CriteriaParamsBinder<>(criteria, helper).assembleCriteriaParamsByEntityValue();

        assertEquals(1, helper.getQueryCriteriaParams().size());
        assertEquals("name", helper.getQueryCriteriaParams().get(0).getName());
        assertTrue(helper.getQueryCriteriaParams().stream()
                .noneMatch(p -> "pageNo".equalsIgnoreCase(p.getName()) || "pageSize".equalsIgnoreCase(p.getName())));
    }

    @Test
    void assemble_betweenRequiresExactlyTwoParts() {
        UserCriteria criteria = new UserCriteria();
        List<QueryCriteriaParam<Object>> params = new ArrayList<>();
        params.add(new QueryCriteriaParam<>("age", QueryConstants.BETWEEN, "1,2,3"));
        criteria.setCriteriaParams(params);

        BuildHelper<Object> helper = new BuildHelper<>(Object.class, new ArrayList<>());
        helper.init(criteria);

        assertThrows(QueryCriteriaException.class,
                () -> new CriteriaParamsBinder<>(criteria, helper).assembleCriteriaParamsByEntityValue());
    }

    @Test
    void assemble_betweenTwoParts_ok() {
        UserCriteria criteria = new UserCriteria();
        List<QueryCriteriaParam<Object>> params = new ArrayList<>();
        params.add(new QueryCriteriaParam<>("age", QueryConstants.BETWEEN, "1,18"));
        criteria.setCriteriaParams(params);

        BuildHelper<Object> helper = new BuildHelper<>(Object.class, new ArrayList<>());
        helper.init(criteria);

        new CriteriaParamsBinder<>(criteria, helper).assembleCriteriaParamsByEntityValue();
        QueryCriteriaParam<Object> bet = helper.getQueryCriteriaParams().stream()
                .filter(p -> QueryConstants.BETWEEN.equalsIgnoreCase(p.getOperation()))
                .findFirst().orElseThrow();
        assertEquals(2, bet.getValues().length);
        assertEquals("1", bet.getValues()[0]);
        assertEquals("18", bet.getValues()[1]);
    }
}
