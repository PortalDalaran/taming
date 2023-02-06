package io.github.portaldalaran.taming.core;

import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.QueryConstants;

import java.util.List;

public class EntityParamNames {
    public String prefixParamName;
    public String paramName;

    EntityParamNames(String prefixParamName, String paramName) {
        this.prefixParamName = prefixParamName;
        this.paramName = paramName;
    }

    /**
     * @return
     */
    public Boolean isOperatorByPrefixParamName() {
        return (QueryConstants.OR.equalsIgnoreCase(prefixParamName) || QueryConstants.AND.equalsIgnoreCase(prefixParamName) || QueryConstants.NESTED.equalsIgnoreCase(prefixParamName));
    }

    public Boolean isOperatorByParamName() {
        return (QueryConstants.OR.equalsIgnoreCase(paramName) || QueryConstants.AND.equalsIgnoreCase(paramName) || QueryConstants.NESTED.equalsIgnoreCase(paramName));
    }

    public Boolean isApplySqlOperator() {
        return QueryConstants.APPLY_SQL.equalsIgnoreCase(prefixParamName);
    }

    public Boolean isNoneOperator() {
        return (!QueryConstants.FIELDS.equalsIgnoreCase(paramName) && !QueryConstants.GROUP_BY.equalsIgnoreCase(paramName)
                && !QueryConstants.ORDER_BY.equalsIgnoreCase(paramName) && !QueryConstants.HAVING.equalsIgnoreCase(paramName)
                && !QueryConstants.PAGE_NO.equalsIgnoreCase(paramName) && !QueryConstants.PAGE_SIZE.equalsIgnoreCase(paramName));
    }

    public Boolean isNumber() {
        try {
            Integer.parseInt(paramName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public QueryCriteriaParam findQueryCriteriaByPrefixParamName(List<QueryCriteriaParam> criteriaParams) {
        return criteriaParams.stream().filter(params -> params.getName().equalsIgnoreCase(prefixParamName)).findFirst().orElse(null);
    }

    public QueryCriteriaParam findQueryCriteriaByParamName(List<QueryCriteriaParam> criteriaParams) {
        return criteriaParams.stream().filter(params -> params.getName().equalsIgnoreCase(paramName)).findFirst().orElse(null);
    }
}