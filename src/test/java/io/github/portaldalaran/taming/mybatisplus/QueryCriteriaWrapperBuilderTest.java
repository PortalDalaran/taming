package io.github.portaldalaran.taming.mybatisplus;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.support.TestEntities;
import io.github.portaldalaran.taming.utils.QueryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主入口 QueryCriteriaWrapperBuilder 的构造、静态工厂与端到端拼装。
 */
class QueryCriteriaWrapperBuilderTest {

    @BeforeEach
    void setUp() {
        TestEntities.initTableInfo();
    }

    @Test
    void staticFactories_createUsableBuilder() {
        QueryCriteriaWrapperBuilder<TestEntities.UserDO> builder =
                QueryCriteriaWrapperBuilder.of(TestEntities.UserDO.class);
        assertNotNull(builder.getQueryWrapper());
        assertSame(TestEntities.UserDO.class, builder.getQueryWrapper().getEntityClass());
        assertTrue(builder.getAssociationQueryFields().isEmpty());

        QueryWrapper<TestEntities.UserDO> custom = new QueryWrapper<>();
        QueryCriteriaWrapperBuilder<TestEntities.UserDO> withWrapper =
                QueryCriteriaWrapperBuilder.of(TestEntities.UserDO.class, custom);
        assertSame(custom, withWrapper.getQueryWrapper());
    }

    @Test
    void legacyConstructors_inferEntityFromAnonymousSubclass() {
        QueryCriteriaWrapperBuilder<TestEntities.UserDO> bySubclass =
                new QueryCriteriaWrapperBuilder<TestEntities.UserDO>() {
                };
        assertSame(TestEntities.UserDO.class, bySubclass.getQueryWrapper().getEntityClass());

        QueryWrapper<TestEntities.UserDO> custom = new QueryWrapper<>();
        QueryCriteriaWrapperBuilder<TestEntities.UserDO> withWrapper =
                new QueryCriteriaWrapperBuilder<TestEntities.UserDO>(custom) {
                };
        assertSame(custom, withWrapper.getQueryWrapper());
    }

    @Test
    void invalidConstructors_throwClearExceptions() {
        assertThrows(QueryCriteriaException.class, () -> QueryCriteriaWrapperBuilder.of(null));
        assertThrows(QueryCriteriaException.class,
                () -> QueryCriteriaWrapperBuilder.of(null, new QueryWrapper<>()));
        assertThrows(QueryCriteriaException.class,
                () -> new QueryCriteriaWrapperBuilder<TestEntities.UserDO>());
        // 带自定义 wrapper 的匿名子类写法仍然兼容
        assertDoesNotThrow(() -> new QueryCriteriaWrapperBuilder<TestEntities.UserDO>(new QueryWrapper<>()) {
        });
    }

    @Test
    void build_assemblesWhereSelectGroupHavingOrder() {
        TestEntities.UserCriteria criteria = new TestEntities.UserCriteria();
        criteria.setName("wang");
        criteria.addQueryCriteriaParam("age", QueryConstants.GT, 18);
        criteria.setFields("id,name,age");
        criteria.setGroupBy("name");
        criteria.setHaving("count(id)>1");
        criteria.setOrderBy("name@desc");

        QueryCriteriaWrapperBuilder<TestEntities.UserDO> builder =
                QueryCriteriaWrapperBuilder.of(TestEntities.UserDO.class);
        assertTrue(builder.build(criteria));

        QueryWrapper<TestEntities.UserDO> wrapper = builder.getQueryWrapper();
        String where = wrapper.getCustomSqlSegment().toLowerCase();
        String all = (where + " " + wrapper.getSqlSegment() + " "
                + wrapper.getExpression().getHaving().getSqlSegment()).toLowerCase();

        assertTrue(where.contains("name ="), () -> where);
        assertTrue(where.contains("age >"), () -> where);
        assertNotNull(wrapper.getSqlSelect());
        assertTrue(wrapper.getSqlSelect().contains("id"), wrapper::getSqlSelect);
        assertTrue(wrapper.getSqlSelect().contains("count(name) as name_count"), wrapper::getSqlSelect);
        assertTrue(all.contains("group by name"), () -> all);
        assertTrue(all.contains("having count(id)>1"), () -> all);
        assertTrue(all.contains("name desc"), () -> all);
        assertTrue(builder.getAssociationQueryFields().isEmpty());
    }

    @Test
    void build_withoutFields_leavesSelectEmpty() {
        TestEntities.UserCriteria criteria = new TestEntities.UserCriteria();
        criteria.setName("wang");

        QueryCriteriaWrapperBuilder<TestEntities.UserDO> builder =
                QueryCriteriaWrapperBuilder.of(TestEntities.UserDO.class);
        assertTrue(builder.build(criteria, new ArrayList<>()));
        assertTrue(builder.getQueryWrapper().getSqlSelect() == null
                || builder.getQueryWrapper().getSqlSelect().isBlank());
        assertTrue(builder.getQueryWrapper().getCustomSqlSegment().contains("name ="));
    }

    @Test
    void build_withJoinClass_resolvesColumnFromJoinEntity() {
        TestEntities.UserCriteria criteria = new TestEntities.UserCriteria();
        criteria.addQueryCriteriaParam("deptName", QueryConstants.EQ, "R&D");

        QueryCriteriaWrapperBuilder<TestEntities.UserDO> builder =
                QueryCriteriaWrapperBuilder.of(TestEntities.UserDO.class);
        assertTrue(builder.build(criteria, List.of(TestEntities.DeptDO.class)));
        assertTrue(builder.getQueryWrapper().getCustomSqlSegment().contains("dept_name ="),
                builder.getQueryWrapper()::getCustomSqlSegment);
    }

    @Test
    void build_withNullJoinList_isTolerated() {
        TestEntities.UserCriteria criteria = new TestEntities.UserCriteria();
        criteria.setName("wang");
        QueryCriteriaWrapperBuilder<TestEntities.UserDO> builder =
                QueryCriteriaWrapperBuilder.of(TestEntities.UserDO.class);
        assertTrue(builder.build(criteria, null));
        assertFalse(builder.getQueryWrapper().getCustomSqlSegment().isBlank());
    }

    @Test
    void build_rejectsUnknownAttribute() {
        TestEntities.UserCriteria criteria = new TestEntities.UserCriteria();
        criteria.addQueryCriteriaParam("ghostField", QueryConstants.EQ, "x");
        QueryCriteriaWrapperBuilder<TestEntities.UserDO> builder =
                QueryCriteriaWrapperBuilder.of(TestEntities.UserDO.class);
        assertThrows(QueryCriteriaException.class, () -> builder.build(criteria));
    }
}
