package io.github.portaldalaran.taming.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.QueryConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhereParamsBuilderApplySqlTest {

    @Data
    @EqualsAndHashCode(callSuper = true)
    static class UserCriteria extends QueryCriteria<Object> {
        private String name;
        private Integer age;
    }

    private WhereParamsBuilder<Object> whereParamsBuilder;
    private QueryWrapper<Object> wrapper;

    @BeforeEach
    void setUp() {
        UserCriteria criteria = new UserCriteria();
        BuildHelper<Object> helper = new BuildHelper<>(Object.class, new ArrayList<>());
        helper.init(criteria);
        whereParamsBuilder = new WhereParamsBuilder<>(helper);
        wrapper = new QueryWrapper<>();
    }

    @Test
    void applySql_indexedParams_expandsToSqlAndArgs() {
        List<QueryCriteriaParam<Object>> indexed = Arrays.asList(
                new QueryCriteriaParam<>("1", null, "2008-08-08"),
                new QueryCriteriaParam<>("0", null, "date_format(date_column,'%Y-%m-%d') = {0}")
        );
        QueryCriteriaParam<Object> param =
                new QueryCriteriaParam<>("applySql", QueryConstants.APPLY_SQL, indexed);

        assertDoesNotThrow(() -> whereParamsBuilder.buildCriteriaParam(wrapper, param));
        String sql = wrapper.getCustomSqlSegment();
        assertTrue(sql != null && sql.toLowerCase().contains("date_format"),
                "expected apply SQL in wrapper, got: " + sql);
    }

    @Test
    void operation_caseInsensitive_eq() {
        // 无 TableInfo 时 eq 会在解析列名阶段失败；此处只验证算子大小写能通过 isSupported 并到达列解析
        QueryCriteriaParam<Object> param = new QueryCriteriaParam<>("name", "EQ", "wang");
        try {
            whereParamsBuilder.buildCriteriaParam(wrapper, param);
        } catch (Exception ex) {
            assertTrue(ex.getMessage() == null
                            || !ex.getMessage().contains("Unsupported query operation"),
                    "EQ should be accepted, got: " + ex.getMessage());
            assertTrue(ex.getMessage() != null && ex.getMessage().contains("Cannot resolve column"),
                    "expected column resolve failure, got: " + ex.getMessage());
        }
    }

    @Test
    void applySql_insideOr_doesNotTreatIndexedArgsAsAttributes() {
        List<QueryCriteriaParam<Object>> indexed = Arrays.asList(
                new QueryCriteriaParam<>("0", null, "age > {0}"),
                new QueryCriteriaParam<>("1", null, "18")
        );
        QueryCriteriaParam<Object> apply =
                new QueryCriteriaParam<>("applySql", QueryConstants.APPLY_SQL, indexed);
        QueryCriteriaParam<Object> or =
                new QueryCriteriaParam<>("or", null, Collections.singletonList(apply));

        assertDoesNotThrow(() -> whereParamsBuilder.buildCriteriaParam(wrapper, or));
    }
}
