package io.github.portaldalaran.taming.pojo;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 查询条件
 *
 * @author aohee@163.com
 */
@Data
public abstract class QueryCriteria<T> {
    /**
     * result fields
     */
    private String fields;

    private String orderBy;

    private String groupBy;

    private String having;

    private List<SelectAssociationFields> selectAssociationFields;

    private List<QueryCriteriaParam<T>> criteriaParams;

    public void addSelectAssField(String entityName, List<String> fieldNames) {
        if (Objects.isNull(selectAssociationFields)) {
            selectAssociationFields = new ArrayList<>();
        }
        selectAssociationFields.add(new SelectAssociationFields(entityName, fieldNames));
    }

    public void addQueryCriteriaParam(String name, String operation, Object... values) {
        if (Objects.isNull(criteriaParams)) {
            criteriaParams = new ArrayList<>();
        }
        criteriaParams.add(new QueryCriteriaParam<>(name, operation, values));
    }

    public void addQueryCriteriaParam(SFunction<T, String> column, String operation, Object... values) {
        if (Objects.isNull(criteriaParams)) {
            criteriaParams = new ArrayList<>();
        }
        criteriaParams.add(new QueryCriteriaParam<>(column, operation, values));
    }
}
