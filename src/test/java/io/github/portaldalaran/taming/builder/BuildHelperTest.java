package io.github.portaldalaran.taming.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.support.TestEntities;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BuildHelper 的字段白名单合并、列名解析与各类 Builder 工厂。
 */
class BuildHelperTest {

    @Data
    @EqualsAndHashCode(callSuper = true)
    static class SlimCriteria extends QueryCriteria<TestEntities.UserDO> {
        private String name;
    }

    private BuildHelper<TestEntities.UserDO> helper;
    private SlimCriteria criteria;

    @BeforeEach
    void setUp() {
        TestEntities.initTableInfo();
        criteria = new SlimCriteria();
        helper = new BuildHelper<>(TestEntities.UserDO.class, new ArrayList<>());
        helper.init(criteria);
    }

    @Test
    void init_mergesVoEntityAndJoinFields_caseInsensitiveAndNoDuplicates() {
        BuildHelper<TestEntities.UserDO> joinHelper = new BuildHelper<>(
                TestEntities.UserDO.class, List.of(TestEntities.DeptDO.class));
        joinHelper.init(criteria);

        List<String> names = joinHelper.getBuildEntityFieldNames();
        assertTrue(names.contains("name"));
        // 实体 DO 上未声明在 VO 中的字段
        assertTrue(names.contains("id"));
        assertTrue(names.contains("createdAt"));
        // join 类字段
        assertTrue(names.contains("deptName"));
        // 同名 id 不重复（忽略大小写去重）
        assertEquals(1, names.stream().filter("id"::equalsIgnoreCase).count());
        assertEquals(names.size(), joinHelper.getBuildEntityFields().size());
    }

    @Test
    void checkEntityAttribute_acceptsGroupOperatorsAndKnownFields() {
        assertTrue(helper.checkEntityAttribute("or"));
        assertTrue(helper.checkEntityAttribute("AND"));
        assertTrue(helper.checkEntityAttribute("NeStEd"));
        assertTrue(helper.checkEntityAttribute("NAME"));
        assertTrue(helper.checkEntityAttribute("createdAt"));
        assertFalse(helper.checkEntityAttribute("unknown"));
        assertFalse(helper.checkEntityAttribute(null));
    }

    @Test
    void getColumn_resolvesFromModelAndFallsBackToJoinClass() {
        assertEquals("name", helper.getColumn("name"));
        assertEquals("sex_code", helper.getColumn("sex"));
        assertEquals("created_at", helper.getColumn("createdAt"));

        BuildHelper<TestEntities.UserDO> joinHelper = new BuildHelper<>(
                TestEntities.UserDO.class, List.of(TestEntities.DeptDO.class));
        joinHelper.init(criteria);
        assertEquals("dept_name", joinHelper.getColumn("deptName"));
    }

    @Test
    void addColumnToQueryFields_addsOnlyKnownDistinctColumns() {
        List<String> fields = new ArrayList<>();
        helper.addColumnToQueryFields(fields, "name");
        helper.addColumnToQueryFields(fields, "name");
        helper.addColumnToQueryFields(fields, "unknownField");
        assertEquals(List.of("name"), fields);
    }

    @Test
    void factories_returnConfiguredBuilders() {
        assertNotNull(helper.newCriteriaParamsBinder(criteria));
        assertNotNull(helper.newColumnsBuilder());
        assertNotNull(helper.newGroupParamsBuilder(new QueryWrapper<>(), criteria));
        assertNotNull(helper.newWhereParamsBuilder());
        assertNotNull(helper.newOrderBuilder(new QueryWrapper<>()));
        assertNotNull(helper.getAssociationQueryFields());
        assertTrue(helper.getAssociationQueryFields().isEmpty());
        assertSame(TestEntities.UserDO.class, helper.getModelClass());
    }

    @Test
    void queryCriteriaParams_canBeSetAndRead() {
        helper.setQueryCriteriaParams(new ArrayList<>());
        assertNotNull(helper.getQueryCriteriaParams());
        assertEquals(0, helper.getQueryCriteriaParams().size());
    }

    @Test
    void constructor_toleratesNullJoinList() {
        BuildHelper<TestEntities.UserDO> nullJoin = new BuildHelper<>(TestEntities.UserDO.class, null);
        nullJoin.init(criteria);
        assertNotNull(nullJoin.getJoinClassList());
        assertTrue(nullJoin.getJoinClassList().isEmpty());
        assertTrue(nullJoin.checkEntityAttribute("id"));
    }

    @Test
    void init_withJoinClassList_sharesFieldsAcrossMultipleClasses() {
        BuildHelper<TestEntities.UserDO> multi = new BuildHelper<>(
                TestEntities.UserDO.class, Arrays.asList(TestEntities.DeptDO.class, TestEntities.UserDO.class));
        multi.init(criteria);
        assertEquals(1, multi.getBuildEntityFieldNames().stream()
                .filter("deptName"::equalsIgnoreCase).count());
    }
}
