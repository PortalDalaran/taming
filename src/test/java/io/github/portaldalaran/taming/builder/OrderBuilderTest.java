package io.github.portaldalaran.taming.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.pojo.PageCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderBuilderTest {

    @Data
    @EqualsAndHashCode(callSuper = true)
    static class UserCriteria extends QueryCriteria<Object> {
        private String name;
        private Integer age;
    }

    @Data
    static class MaterialModel {
        private Long id;
        private Integer sortOrder;
        private String fileName;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    static class MaterialPageCriteria extends PageCriteria<MaterialModel> {
        private String fileName;
        private Integer sortOrder;
    }

    private OrderBuilder<Object> orderBuilder;

    @BeforeEach
    void setUp() {
        UserCriteria criteria = new UserCriteria();
        BuildHelper<Object> helper = new BuildHelper<>(Object.class, new ArrayList<>());
        helper.init(criteria);
        orderBuilder = new OrderBuilder<>(new QueryWrapper<>(), helper);
    }

    @Test
    void invalidOrderByAttribute_throws() {
        assertThrows(QueryCriteriaException.class, () -> orderBuilder.buildOrderBy("unknown@desc"));
    }

    @Test
    void injectionInOrderBy_throws() {
        assertThrows(QueryCriteriaException.class, () -> orderBuilder.buildOrderBy("name';--@desc"));
    }

    @Test
    void invalidDirection_throws() {
        assertThrows(QueryCriteriaException.class, () -> orderBuilder.buildOrderBy("name@hack"));
    }

    /**
     * PageReqVO 常不声明 id，但前端默认 orderBy 会带 id；白名单须含 modelClass 字段。
     */
    @Test
    void orderById_allowedWhenOnlyOnModel() {
        MaterialPageCriteria criteria = new MaterialPageCriteria();
        BuildHelper<MaterialModel> helper = new BuildHelper<>(MaterialModel.class, new ArrayList<>());
        helper.init(criteria);

        assertTrue(helper.checkEntityAttribute("id"));
        assertTrue(helper.checkEntityAttribute("sortOrder"));
        // getColumn 依赖 MP TableInfo，单测只校验白名单；不因 id 抛 Invalid orderBy attribute
        OrderBuilder<MaterialModel> builder = new OrderBuilder<>(new QueryWrapper<>(), helper);
        try {
            builder.buildOrderBy("sortOrder@asc,id@asc");
        } catch (QueryCriteriaException ex) {
            assertTrue(!ex.getMessage().startsWith("Invalid orderBy attribute"),
                    () -> "不应再因 VO 缺 id 拒绝 orderBy: " + ex.getMessage());
        }
    }
}
