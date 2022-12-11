package io.github.portaldalaran.taming.pojo;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * query criteria ex {name@eq: david}
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
    /**
     * 值
     */
    private Object value;

    /**
     * 第二个值，用为between
     */
    private Object value2;

    private SFunction<T, ?> column;

    public QueryCriteriaParam(String name, String operation, Object value ) {
        this.name = name;
        this.operation = operation;
        this.value = value;
    }
    public QueryCriteriaParam(String name, String operation, Object value,Object value2 ) {
        this.name = name;
        this.operation = operation;
        this.value = value;
        this.value2 = value2;
    }

    /**
     * 后续的实现没有写，使用没效果
     * @param column
     * @param operation
     * @param value
     */
    @Deprecated
    public QueryCriteriaParam(SFunction<T, ?> column, String operation, Object value ) {
        this.column = column;
        this.operation = operation;
        this.value = value;
    }
}
