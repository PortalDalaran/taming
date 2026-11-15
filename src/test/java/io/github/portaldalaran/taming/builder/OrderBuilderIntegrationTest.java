package io.github.portaldalaran.taming.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ORDER BY 的方向、列名映射与校验。
 */
class OrderBuilderIntegrationTest {

    private OrderBuilder<TestEntities.UserDO> builder;
    private QueryWrapper<TestEntities.UserDO> wrapper;

    @BeforeEach
    void setUp() {
        TestEntities.initTableInfo();
        TestEntities.UserCriteria criteria = new TestEntities.UserCriteria();
        BuildHelper<TestEntities.UserDO> helper =
                new BuildHelper<>(TestEntities.UserDO.class, new ArrayList<>());
        helper.init(criteria);
        wrapper = new QueryWrapper<>();
        builder = helper.newOrderBuilder(wrapper);
    }

    private String sql() {
        return (wrapper.getCustomSqlSegment() + " " + wrapper.getSqlSegment()).toLowerCase();
    }

    @Test
    void orderBy_multipleFieldsAndDirections() {
        assertSame(wrapper, builder.buildOrderBy("name@desc,age@asc"));
        String sql = sql();
        assertTrue(sql.contains("order by"), () -> sql);
        assertTrue(sql.contains("name desc"), () -> sql);
        assertTrue(sql.contains("age asc"), () -> sql);
    }

    @Test
    void orderBy_withoutDirection_defaultsToAsc() {
        builder.buildOrderBy("name");
        assertTrue(sql().contains("name asc"), () -> sql());
    }

    @Test
    void orderBy_mapsFieldToTableColumn() {
        builder.buildOrderBy("sex@DESC,createdAt@asc");
        String sql = sql();
        assertTrue(sql.contains("sex_code desc"), () -> sql);
        assertTrue(sql.contains("created_at asc"), () -> sql);
    }

    @Test
    void orderBy_caseInsensitiveFieldAndDirection() {
        builder.buildOrderBy("NAME@DESC");
        assertTrue(sql().contains("name desc"), () -> sql());
    }

    @Test
    void orderBy_nullOrBlank_isNoop() {
        assertSame(wrapper, builder.buildOrderBy(null));
        assertEquals("", wrapper.getCustomSqlSegment().trim());
        builder.buildOrderBy(" , ");
        assertEquals("", wrapper.getCustomSqlSegment().trim());
    }

    @Test
    void orderBy_invalidDirectionAttributeOrInjection_throws() {
        assertThrows(QueryCriteriaException.class, () -> builder.buildOrderBy("name@hack"));
        assertThrows(QueryCriteriaException.class, () -> builder.buildOrderBy("unknownField@desc"));
        assertThrows(QueryCriteriaException.class, () -> builder.buildOrderBy("name';--@desc"));
        assertThrows(QueryCriteriaException.class, () -> builder.buildOrderBy("name@desc;drop"));
    }
}
