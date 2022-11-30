package io.github.portaldalaran.taming.pojo;

import lombok.Data;

import java.util.List;

/**
 * 查询条件
 *
 * @author aohee@163.com
 */
@Data
public class QueryCriteria<T> {
    /**
     * result fields
     */
    private String fields;

    private String orderBy;

    private String groupBy;

    private String having;

    private List<SelectAssociationFields> selectAssociationFields;

    private List<QueryCriteriaParam<T>> criteriaParams;
}
