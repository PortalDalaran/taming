package io.github.portaldalaran.taming.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.support.TestEntities;
import io.github.portaldalaran.taming.utils.QueryConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖所有 WHERE 算子的 SQL 生成、参数绑定、列名映射与异常分支。
 */
class WhereParamsBuilderOperatorTest {

    @Data
    @EqualsAndHashCode(callSuper = true)
    static class GhostCriteria extends QueryCriteria<TestEntities.UserDO> {
        private String ghost;
    }

    private WhereParamsBuilder<TestEntities.UserDO> builder;
    private QueryWrapper<TestEntities.UserDO> wrapper;

    @BeforeEach
    void setUp() {
        TestEntities.initTableInfo();
        TestEntities.UserCriteria criteria = new TestEntities.UserCriteria();
        BuildHelper<TestEntities.UserDO> helper =
                new BuildHelper<>(TestEntities.UserDO.class, new ArrayList<>());
        helper.init(criteria);
        builder = new WhereParamsBuilder<>(helper);
        wrapper = new QueryWrapper<>();
    }

    private String sql() {
        return (wrapper.getCustomSqlSegment() + " " + wrapper.getSqlSegment()).toLowerCase();
    }

    private Map<String, Object> params() {
        // MP 的参数绑定发生在 SQL 片段渲染时，先触发渲染再读取参数表
        wrapper.getSqlSegment();
        wrapper.getCustomSqlSegment();
        return wrapper.getParamNameValuePairs();
    }

    private void build(String field, String operation, Object... values) {
        builder.buildCriteriaParam(wrapper, new QueryCriteriaParam<>(field, operation, values));
    }

    static Stream<Arguments> operatorCases() {
        return Stream.of(
                Arguments.of("name", "eq", new Object[]{"wang"}, "name ="),
                Arguments.of("name", "ne", new Object[]{"wang"}, "name <>"),
                Arguments.of("age", "ge", new Object[]{18}, "age >="),
                Arguments.of("age", "gt", new Object[]{18}, "age >"),
                Arguments.of("age", "le", new Object[]{18}, "age <="),
                Arguments.of("age", "lt", new Object[]{18}, "age <"),
                Arguments.of("name", "like", new Object[]{"wang"}, "name like"),
                Arguments.of("name", "notLike", new Object[]{"wang"}, "name not like"),
                Arguments.of("name", "startWith", new Object[]{"wang"}, "name like"),
                Arguments.of("name", "endWith", new Object[]{"wang"}, "name like"),
                Arguments.of("id", "in", new Object[]{"1,2,3"}, "id in ("),
                Arguments.of("id", "notIn", new Object[]{"1,2,3"}, "id not in ("),
                Arguments.of("age", "bet", new Object[]{18, 30}, "age between"),
                Arguments.of("id", "inSql", new Object[]{"select id from t_dept where id < 3"}, "id in ("),
                Arguments.of("id", "notInSql", new Object[]{"select id from t_dept where id < 3"}, "id not in ("),
                Arguments.of("age", "null", new Object[0], "age is null"),
                Arguments.of("age", "notNull", new Object[0], "age is not null"),
                // 算子大小写归一化
                Arguments.of("name", "EQ", new Object[]{"wang"}, "name ="),
                Arguments.of("name", "NotLike", new Object[]{"wang"}, "name not like"),
                Arguments.of("name", "STARTWITH", new Object[]{"wang"}, "name like")
        );
    }

    @ParameterizedTest(name = "{1} => {3}")
    @MethodSource("operatorCases")
    @DisplayName("每个算子都生成预期的 SQL 片段")
    void operator_generatesExpectedSql(String field, String operation, Object[] values, String expected) {
        assertDoesNotThrow(() -> build(field, operation, values));
        assertTrue(sql().contains(expected),
                () -> "expected [" + expected + "] in SQL: " + sql());
    }

    @Test
    @DisplayName("like / startWith / endWith 的通配符位置正确")
    void likeVariants_wrapValuesCorrectly() {
        build("name", QueryConstants.LIKE, "wang");
        assertTrue(params().containsValue("%wang%"));

        wrapper = new QueryWrapper<>();
        build("name", QueryConstants.START_WITH, "wang");
        assertTrue(params().containsValue("wang%"));

        wrapper = new QueryWrapper<>();
        build("name", QueryConstants.END_WITH, "wang");
        assertTrue(params().containsValue("%wang"));
    }

    @Test
    @DisplayName("in / notIn 逗号串展开为多个绑定参数")
    void in_expandsCommaSeparatedValues() {
        build("age", QueryConstants.IN, "1,2,3");
        assertEquals(3, params().size());
        assertTrue(params().containsValue("1"));
        assertTrue(params().containsValue("2"));
        assertTrue(params().containsValue("3"));
        assertTrue(sql().contains("age in ("));
    }

    @Test
    @DisplayName("in / notIn 支持直接传集合")
    void in_acceptsCollectionValue() {
        build("age", QueryConstants.IN, Arrays.asList(1, 2));
        assertEquals(2, params().size());
        assertTrue(sql().contains("age in ("));
    }

    @Test
    @DisplayName("bet 绑定两个参数")
    void between_bindsTwoValues() {
        build("age", QueryConstants.BETWEEN, 18, 30);
        assertEquals(2, params().size());
        assertTrue(params().containsValue(18));
        assertTrue(params().containsValue(30));
        assertTrue(sql().contains("between"));
    }

    @Test
    @DisplayName("eq/ne 遇到 null 值转为 IS NULL / IS NOT NULL")
    void nullValue_semantics() {
        build("name", QueryConstants.EQ, (Object) null);
        assertTrue(sql().contains("is null"));

        wrapper = new QueryWrapper<>();
        build("name", QueryConstants.NE, (Object) null);
        assertTrue(sql().contains("is not null"));
    }

    @Test
    @DisplayName("无算子时按等值处理，null 值生成 IS NULL")
    void noOperation_defaultsToEquals() {
        build("name", null, "wang");
        assertTrue(sql().contains("name ="));

        wrapper = new QueryWrapper<>();
        build("name", null, (Object) null);
        assertTrue(sql().contains("is null"));
    }

    @Test
    @DisplayName("字段映射到 @TableField / 驼峰转下划线列名")
    void columnMapping_usesTableInfo() {
        build("sex", QueryConstants.EQ, "male");
        assertTrue(sql().contains("sex_code ="), () -> sql());

        wrapper = new QueryWrapper<>();
        build("createdAt", QueryConstants.GT, "2024-01-01 00:00:00");
        assertTrue(sql().contains("created_at >"), () -> sql());
    }

    @Test
    @DisplayName("方法引用构造的 QueryCriteriaParam 可解析列名")
    void lambdaColumnParam_resolvesColumn() {
        QueryCriteriaParam<TestEntities.UserDO> param =
                new QueryCriteriaParam<>(TestEntities.UserDO::getName, QueryConstants.EQ, "wang");
        assertEquals("name", param.getName());
        builder.buildCriteriaParam(wrapper, param);
        assertTrue(sql().contains("name ="), () -> sql());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pageNo", "pageSize", "orderBy", "groupBy", "having", "fields", "sortOrder", "ORDERBY"})
    @DisplayName("保留字段不进入 WHERE")
    void commonFields_areSkipped(String field) {
        build(field, QueryConstants.EQ, "x");
        assertEquals("", wrapper.getCustomSqlSegment().trim());
    }

    @Test
    @DisplayName("or / and / nested 生成括号分组（空 wrapper 上 MP 不输出连接词）")
    void nestedGroups_generateParentheses() {
        List<QueryCriteriaParam<TestEntities.UserDO>> children = Arrays.asList(
                new QueryCriteriaParam<>("name", QueryConstants.EQ, "wang"),
                new QueryCriteriaParam<>("age", QueryConstants.GT, 18));
        build("or", null, children);
        String segment = sql();
        assertTrue(segment.contains("("), () -> segment);
        assertTrue(segment.contains("name ="), () -> segment);
        assertTrue(segment.contains("age >"), () -> segment);

        wrapper = new QueryWrapper<>();
        build("and", null, children);
        assertTrue(sql().contains("name ="), () -> sql());
        assertTrue(sql().contains("age >"), () -> sql());

        wrapper = new QueryWrapper<>();
        build("nested", null, children);
        assertTrue(sql().contains("("), () -> sql());
        assertTrue(sql().contains("age >"), () -> sql());
    }

    @Test
    @DisplayName("or 子节点中的非法属性在预校验阶段被拒绝")
    void nestedGroup_rejectsInvalidChild() {
        List<QueryCriteriaParam<TestEntities.UserDO>> children = Collections.singletonList(
                new QueryCriteriaParam<>("unknownField", QueryConstants.EQ, 1));
        assertThrows(QueryCriteriaException.class, () -> build("or", null, children));
    }

    @Test
    @DisplayName("or 子节点类型不合法时抛出明确异常")
    void nestedGroup_rejectsWrongChildType() {
        assertThrows(QueryCriteriaException.class,
                () -> build("or", null, Collections.singletonList("not-a-param")));
    }

    @Test
    @DisplayName("缺失比较值 / 非法算子 / 未知列 均抛出 QueryCriteriaException")
    void invalidInputs_throw() {
        assertThrows(QueryCriteriaException.class,
                () -> build("age", QueryConstants.GE, new Object[0]));
        assertThrows(QueryCriteriaException.class,
                () -> build("name", "hackOp", "x"));
        assertThrows(QueryCriteriaException.class,
                () -> build("age", QueryConstants.BETWEEN, 1));
        assertThrows(QueryCriteriaException.class,
                () -> build("age", QueryConstants.BETWEEN, 1, null));
        assertThrows(QueryCriteriaException.class,
                () -> build("name", QueryConstants.LIKE, (Object) null));
        assertThrows(QueryCriteriaException.class,
                () -> build("name';--", QueryConstants.EQ, "x"));
    }

    @Test
    @DisplayName("白名单内但无法解析列的字段抛出明确异常")
    void unresolvedColumn_throws() {
        GhostCriteria criteria = new GhostCriteria();
        BuildHelper<TestEntities.UserDO> helper =
                new BuildHelper<>(TestEntities.UserDO.class, new ArrayList<>());
        helper.init(criteria);
        WhereParamsBuilder<TestEntities.UserDO> ghostBuilder = new WhereParamsBuilder<>(helper);

        QueryCriteriaException ex = assertThrows(QueryCriteriaException.class,
                () -> ghostBuilder.buildCriteriaParam(new QueryWrapper<>(),
                        new QueryCriteriaParam<>("ghost", QueryConstants.EQ, "x")));
        assertTrue(ex.getMessage().contains("Cannot resolve column"), ex.getMessage());
    }

    @Test
    @DisplayName("inSql 中的堆叠语句被 SQL 注入校验拦截")
    void inSql_rejectsStackedQuery() {
        assertThrows(QueryCriteriaException.class,
                () -> build("id", QueryConstants.IN_SQL, "1=1; select * from user"));
    }

    @Test
    @DisplayName("applySql 带占位参数时展开为绑定参数")
    void applySql_withIndexedArgs() {
        List<QueryCriteriaParam<TestEntities.UserDO>> indexed = Arrays.asList(
                new QueryCriteriaParam<>("0", null, "date_format(created_at,'%Y-%m-%d') = {0}"),
                new QueryCriteriaParam<>("1", null, "2008-08-08"));
        build("applySql", QueryConstants.APPLY_SQL, indexed);
        assertTrue(sql().contains("date_format"), () -> sql());
        assertTrue(params().containsValue("2008-08-08"));
    }

    @Test
    @DisplayName("applySql 空值被拒绝")
    void applySql_rejectsBlankValue() {
        assertThrows(QueryCriteriaException.class,
                () -> build("applySql", QueryConstants.APPLY_SQL, ""));
        assertThrows(QueryCriteriaException.class,
                () -> build("applySql", QueryConstants.APPLY_SQL, new Object[0]));
    }

    @Test
    @DisplayName("buildCriteriaParam 返回 true")
    void build_returnsTrue() {
        assertTrue(builder.buildCriteriaParam(wrapper,
                new QueryCriteriaParam<>("name", QueryConstants.EQ, "wang")));
        assertFalse(wrapper.getCustomSqlSegment().isBlank());
    }
}
