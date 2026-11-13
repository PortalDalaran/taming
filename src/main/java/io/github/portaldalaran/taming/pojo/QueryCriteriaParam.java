package io.github.portaldalaran.taming.pojo;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import io.github.portaldalaran.taming.utils.BuildUtils;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * query criteria ex {name@eq: david}
 *
 * @author aohee@163.com
 */
@Data
@NoArgsConstructor
public class QueryCriteriaParam<T> {

    /**
     * 名字
     */
    private String name;
    /**
     * 操作符
     * eq,ne
     */
    private String operation;

    private Object[] values;
    private SFunction<T, ?> column;

    public QueryCriteriaParam(String name, String operation, Object... values) {
        this.name = name;
        this.operation = operation;
        this.values = values;

    }

    /**
     * 通过方法引用指定列；会解析出字段名写入 {@link #name}。
     *
     * @param column    实体字段方法引用
     * @param operation 算子
     * @param values    条件值
     */
    public QueryCriteriaParam(SFunction<T, ?> column, String operation, Object... values) {
        this.column = column;
        this.name = BuildUtils.getFieldName(column);
        this.operation = operation;
        this.values = values;
    }

    public void setValue(Object... values) {
        this.values = values;
    }


    public Object getValue() {
        return Objects.nonNull(this.values) && this.values.length > 0 ? this.values[0] : null;
    }

    public Object getValue2() {
        return Objects.nonNull(this.values) && this.values.length > 1 ? this.values[1] : null;
    }

}
