package io.github.portaldalaran.taming.builder;

import io.github.portaldalaran.talons.exception.TalonsException;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.pojo.SelectAssociationFields;
import io.github.portaldalaran.taming.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 投影字段（fields）与聚合函数别名的构建规则。
 */
class ColumnsBuilderTest {

    private TestEntities.UserCriteria criteria;
    private BuildHelper<TestEntities.UserDO> helper;
    private ColumnsBuilder<TestEntities.UserDO> builder;

    @BeforeEach
    void setUp() {
        TestEntities.initTableInfo();
        criteria = new TestEntities.UserCriteria();
        helper = new BuildHelper<>(TestEntities.UserDO.class, new ArrayList<>());
        helper.init(criteria);
        builder = helper.newColumnsBuilder();
    }

    @Test
    void nullFields_returnsEmptyList() {
        assertTrue(builder.buildFields(criteria).isEmpty());
    }

    @Test
    void plainFields_areMappedToColumnsAndDeduplicated() {
        criteria.setFields("id,name,age,sex,name");
        List<String> fields = builder.buildFields(criteria);
        assertEquals(List.of("id", "name", "age", "sex_code"), fields);
    }

    @Test
    void aggregateFunctions_generateAliases() {
        criteria.setFields("count(age),sum(age),avg(age)");
        List<String> fields = builder.buildFields(criteria);
        assertTrue(fields.contains("age"), () -> fields.toString());
        assertTrue(fields.contains("count(age) as age_count"), () -> fields.toString());
        assertTrue(fields.contains("sum(age) as age_sum"), () -> fields.toString());
        assertTrue(fields.contains("avg(age) as age_avg"), () -> fields.toString());
    }

    @Test
    void functionWithLiteralArgument_mapsOnlyFieldToken() {
        criteria.setFields("date_format(createdAt,'%Y')");
        List<String> fields = builder.buildFields(criteria);
        assertTrue(fields.contains("created_at"), () -> fields.toString());
        assertTrue(fields.contains("date_format(created_at,'%Y') as created_at_date_format"),
                () -> fields.toString());
    }

    @Test
    void unknownField_isSilentlyIgnored() {
        criteria.setFields("id,unknownField");
        List<String> fields = builder.buildFields(criteria);
        assertEquals(List.of("id"), fields);
    }

    @Test
    void unmatchedParenthesis_throws() {
        criteria.setFields("count(age");
        assertThrows(QueryCriteriaException.class, () -> builder.buildFields(criteria));
    }

    @Test
    void stackedStatementInFields_isRejected() {
        criteria.setFields("id; drop table t_user");
        assertThrows(QueryCriteriaException.class, () -> builder.buildFields(criteria));
    }

    @Test
    void unknownAssociationEntity_throws() {
        criteria.setFields("id");
        criteria.addSelectAssField("dept", List.of("deptName"));
        QueryCriteriaException ex = assertThrows(QueryCriteriaException.class,
                () -> builder.buildFields(criteria));
        assertTrue(ex.getMessage().contains("Unknown association entity"), ex.getMessage());
    }

    @Test
    void injectionInAssociationEntityName_throwsTalonsException() {
        criteria.setFields("id");
        criteria.setSelectAssociationFields(List.of(new SelectAssociationFields("dept';--", List.of("x"))));
        assertThrows(TalonsException.class, () -> builder.buildFields(criteria));
    }

    @Test
    void blankFieldTokens_areSkipped() {
        criteria.setFields(" , id, ,age, ");
        List<String> fields = builder.buildFields(criteria);
        assertEquals(List.of("id", "age"), fields);
    }

    @Test
    void fieldsAreDistinct() {
        criteria.setFields("name,name,count(name)");
        List<String> fields = builder.buildFields(criteria);
        assertEquals(fields.size(), fields.stream().distinct().count());
        assertFalse(fields.isEmpty());
    }
}
