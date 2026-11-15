package io.github.portaldalaran.taming.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GROUP BY / HAVING 的 SQL 构建与校验。
 */
class GroupParamsBuilderIntegrationTest {

    private TestEntities.UserCriteria criteria;
    private BuildHelper<TestEntities.UserDO> helper;
    private QueryWrapper<TestEntities.UserDO> wrapper;
    private GroupParamsBuilder<TestEntities.UserCriteria, TestEntities.UserDO> builder;
    private List<String> queryFields;

    @BeforeEach
    void setUp() {
        TestEntities.initTableInfo();
        criteria = new TestEntities.UserCriteria();
        helper = new BuildHelper<>(TestEntities.UserDO.class, new ArrayList<>());
        helper.init(criteria);
        wrapper = new QueryWrapper<>();
        builder = helper.newGroupParamsBuilder(wrapper, criteria);
        queryFields = new ArrayList<>();
    }

    private String sql() {
        // HAVING 片段单独存在时不会进入 getSqlSegment()，需从 expression 的 having 段读取
        return (wrapper.getCustomSqlSegment() + " " + wrapper.getSqlSegment() + " "
                + wrapper.getExpression().getHaving().getSqlSegment()).toLowerCase();
    }

    @Test
    void groupBy_addsCountColumnsAndGroupClause() {
        criteria.setGroupBy("name,age");
        assertSame(wrapper, builder.buildGroupBy(queryFields));

        assertEquals(List.of("name", "count(name) as name_count",
                        "age", "count(age) as age_count"), queryFields);
        assertTrue(sql().contains("group by name,age"), () -> sql());
    }

    @Test
    void groupBy_mapsFieldToColumn() {
        criteria.setGroupBy("sex");
        builder.buildGroupBy(queryFields);
        assertEquals(List.of("sex_code", "count(sex_code) as sex_code_count"), queryFields);
        assertTrue(sql().contains("sex_code"), () -> sql());
    }

    @Test
    void groupBy_nullOrBlank_isNoop() {
        assertSame(wrapper, builder.buildGroupBy(queryFields));
        assertTrue(queryFields.isEmpty());

        criteria.setGroupBy(" , ");
        builder.buildGroupBy(queryFields);
        assertTrue(queryFields.isEmpty());
    }

    @Test
    void groupBy_invalidAttributeOrInjection_throws() {
        criteria.setGroupBy("unknownField");
        assertThrows(QueryCriteriaException.class, () -> builder.buildGroupBy(queryFields));

        criteria.setGroupBy("name;drop");
        assertThrows(QueryCriteriaException.class, () -> builder.buildGroupBy(queryFields));
    }

    @Test
    void having_withAggregateFunction_preservesClause() {
        criteria.setHaving("sum(age)>18");
        assertSame(wrapper, builder.buildHaving());
        assertTrue(sql().contains("having sum(age)>18"), () -> sql());
    }

    @Test
    void having_withoutParenthesis_mapsColumn() {
        criteria.setHaving("sex='male'");
        builder.buildHaving();
        assertTrue(sql().contains("sex_code='male'"), () -> sql());
    }

    @Test
    void having_dateFunction_mapsOnlyFieldToken() {
        criteria.setHaving("date_format(createdAt,'%Y')>2020");
        builder.buildHaving();
        // sql() 统一转小写，因此格式串 %Y 在断言中为 %y
        assertTrue(sql().contains("date_format(created_at,'%y')>2020"), () -> sql());
    }

    @Test
    void having_multipleClauses_arePreserved() {
        criteria.setHaving("count(id)>1,sum(age)>18");
        builder.buildHaving();
        assertTrue(sql().contains("count(id)>1,sum(age)>18"), () -> sql());
    }

    @Test
    void having_null_isNoop() {
        assertSame(wrapper, builder.buildHaving());
        assertEquals("", wrapper.getCustomSqlSegment().trim());
    }

    @Test
    void having_invalidAttributeOrInjection_throws() {
        criteria.setHaving("sum(unknown)>1");
        assertThrows(QueryCriteriaException.class, builder::buildHaving);

        criteria.setHaving("sum(age)>18 -- comment");
        assertThrows(QueryCriteriaException.class, builder::buildHaving);

        criteria.setHaving("1 union select password from user");
        assertThrows(QueryCriteriaException.class, builder::buildHaving);
    }

    @Test
    void having_booleanSuffix_throws() {
        criteria.setHaving("sum(age)>0 OR 1=1");
        assertThrows(QueryCriteriaException.class, builder::buildHaving);

        criteria.setHaving("sex='male' OR 1=1");
        assertThrows(QueryCriteriaException.class, builder::buildHaving);

        criteria.setHaving("age in (select id from t_user)");
        assertThrows(QueryCriteriaException.class, builder::buildHaving);

        criteria.setHaving("sleep(age)>0");
        assertThrows(QueryCriteriaException.class, builder::buildHaving);
    }

    @Test
    void having_inListAndNestedFunction_areRewritten() {
        criteria.setHaving("age in (1, 2, 3)");
        builder.buildHaving();
        assertTrue(sql().contains("age in (1,2,3)"), () -> sql());

        criteria = new TestEntities.UserCriteria();
        wrapper = new QueryWrapper<>();
        builder = helper.newGroupParamsBuilder(wrapper, criteria);
        criteria.setHaving("sum(ifnull(age,0))>0");
        builder.buildHaving();
        assertTrue(sql().contains("sum(ifnull(age,0))>0"), () -> sql());
    }

    @Test
    void splitHavingClauses_handlesQuotesAndUnbalancedParens() {
        assertEquals(List.of(), GroupParamsBuilder.splitHavingClauses(null));
        assertEquals(List.of("a", "b"), GroupParamsBuilder.splitHavingClauses("a,b"));
        assertEquals(List.of("date_format(created,'a,b')"),
                GroupParamsBuilder.splitHavingClauses("date_format(created,'a,b')"));
        assertEquals(-1, GroupParamsBuilder.findMatchingParen("sum(age", 3));
        assertEquals("", GroupParamsBuilder.extractFieldToken(null));
        assertEquals("", GroupParamsBuilder.extractFieldToken("  "));
    }
}
