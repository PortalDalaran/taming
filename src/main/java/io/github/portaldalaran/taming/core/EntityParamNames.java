package io.github.portaldalaran.taming.core;

import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.QueryCriteriaConstants;

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
        return (QueryCriteriaConstants.OR_OPERATOR.equalsIgnoreCase(prefixParamName) || QueryCriteriaConstants.AND_OPERATOR.equalsIgnoreCase(prefixParamName));
    }

    public Boolean isOperatorByParamName() {
        return (QueryCriteriaConstants.OR_OPERATOR.equalsIgnoreCase(paramName) || QueryCriteriaConstants.AND_OPERATOR.equalsIgnoreCase(paramName));
    }

    public Boolean isApplySqlOperator() {
        return QueryCriteriaConstants.APPLY_SQL_OPERATOR.equalsIgnoreCase(prefixParamName);
    }

    public Boolean isNoneOperator() {
        return (!QueryCriteriaConstants.FIELDS_OPERATOR.equalsIgnoreCase(paramName) && !QueryCriteriaConstants.GROUP_BY_OPERATOR.equalsIgnoreCase(paramName)
                && !QueryCriteriaConstants.ORDER_BY_OPERATOR.equalsIgnoreCase(paramName) && !QueryCriteriaConstants.HAVING_OPERATOR.equalsIgnoreCase(paramName)
                && !QueryCriteriaConstants.PAGE_NO.equalsIgnoreCase(paramName) && !QueryCriteriaConstants.PAGE_SIZE.equalsIgnoreCase(paramName));
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