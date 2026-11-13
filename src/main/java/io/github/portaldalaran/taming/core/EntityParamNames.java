package io.github.portaldalaran.taming.core;

import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.QueryConstants;
import lombok.Data;

import java.util.List;

@Data
public class EntityParamNames {
    private String prefixParamName;

    private String paramName;

    EntityParamNames(String prefixParamName, String paramName) {
        this.prefixParamName = prefixParamName;
        this.paramName = paramName;
    }

    public boolean isOperatorByPrefixParamName() {
        return QueryConstants.OR.equalsIgnoreCase(prefixParamName)
                || QueryConstants.AND.equalsIgnoreCase(prefixParamName)
                || QueryConstants.NESTED.equalsIgnoreCase(prefixParamName);
    }

    public boolean isOperatorByParamName() {
        return QueryConstants.OR.equalsIgnoreCase(paramName)
                || QueryConstants.AND.equalsIgnoreCase(paramName)
                || QueryConstants.NESTED.equalsIgnoreCase(paramName);
    }

    public boolean isApplySqlOperator() {
        return QueryConstants.APPLY_SQL.equalsIgnoreCase(prefixParamName);
    }

    /**
     * 判断是否为用户自定义查询字段（非框架保留关键字）
     */
    public boolean isUserDefinedField() {
        return !QueryConstants.FIELDS.equalsIgnoreCase(paramName)
                && !QueryConstants.GROUP_BY.equalsIgnoreCase(paramName)
                && !QueryConstants.ORDER_BY.equalsIgnoreCase(paramName)
                && !QueryConstants.HAVING.equalsIgnoreCase(paramName)
                && !QueryConstants.PAGE_NO.equalsIgnoreCase(paramName)
                && !QueryConstants.PAGE_SIZE.equalsIgnoreCase(paramName);
    }

    public boolean isNumber() {
        if (paramName == null || paramName.isEmpty()) {
            return false;
        }
        return paramName.chars().allMatch(Character::isDigit);
    }

    public QueryCriteriaParam<?> findQueryCriteriaByPrefixParamName(List<QueryCriteriaParam<?>> criteriaParams) {
        return criteriaParams.stream()
                .filter(params -> params.getName() != null && params.getName().equalsIgnoreCase(prefixParamName))
                .findFirst().orElse(null);
    }

    public QueryCriteriaParam<?> findQueryCriteriaByParamName(List<QueryCriteriaParam<?>> criteriaParams) {
        return criteriaParams.stream()
                .filter(params -> params.getName() != null && params.getName().equalsIgnoreCase(paramName))
                .findFirst().orElse(null);
    }
}
