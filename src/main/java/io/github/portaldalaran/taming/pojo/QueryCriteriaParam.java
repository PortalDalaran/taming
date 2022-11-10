package io.github.portaldalaran.taming.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * query criteria ex {name@eq: david}
 * @author aohee@163.com
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class QueryCriteriaParam {
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

}
