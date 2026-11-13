package io.github.portaldalaran.taming.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.QueryConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WhereParamsBuilderTest {

    @Data
    @EqualsAndHashCode(callSuper = true)
    static class UserCriteria extends QueryCriteria<Object> {
        private String name;
        private Integer age;
    }

    private WhereParamsBuilder<Object> whereParamsBuilder;

    @BeforeEach
    void setUp() {
        UserCriteria criteria = new UserCriteria();
        BuildHelper<Object> helper = new BuildHelper<>(Object.class, new ArrayList<>());
        helper.init(criteria);
        whereParamsBuilder = new WhereParamsBuilder<>(helper);
    }

    @Test
    void invalidAttribute_throws() {
        QueryCriteriaParam<Object> param = new QueryCriteriaParam<>("unknownField", QueryConstants.EQ, 1);
        assertThrows(QueryCriteriaException.class,
                () -> whereParamsBuilder.buildCriteriaParam(new QueryWrapper<>(), param));
    }

    @Test
    void blankAttribute_throws() {
        QueryCriteriaParam<Object> param = new QueryCriteriaParam<>("  ", QueryConstants.EQ, 1);
        assertThrows(QueryCriteriaException.class,
                () -> whereParamsBuilder.buildCriteriaParam(new QueryWrapper<>(), param));
    }

    @Test
    void unsupportedOperation_throws() {
        // name 在 VO 属性中，但算子非法；列解析可能失败或算子失败
        QueryCriteriaParam<Object> param = new QueryCriteriaParam<>("name", "hackOp", "x");
        assertThrows(QueryCriteriaException.class,
                () -> whereParamsBuilder.buildCriteriaParam(new QueryWrapper<>(), param));
    }

    @Test
    void orWithNonListValue_throws() {
        QueryCriteriaParam<Object> param = new QueryCriteriaParam<>("or", null, "not-a-list");
        assertThrows(QueryCriteriaException.class,
                () -> whereParamsBuilder.buildCriteriaParam(new QueryWrapper<>(), param));
    }
}
