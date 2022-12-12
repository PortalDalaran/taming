package io.github.portaldalaran.taming.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.BuildUtils;
import io.github.portaldalaran.taming.utils.QueryCriteriaConstants;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class WhereParamsBuilder<T> {
    private BuildHelper<T> buildHelper;

    public WhereParamsBuilder(BuildHelper<T> buildHelper) {
        this.buildHelper = buildHelper;
    }


    /**
     * 处理where条件，拼装到Mybatis的QueryWrapper中
     * Build where condition and assemble it into the QueryWrapper of Mybatis
     *
     * @param wrapper            queryWrapper
     * @param queryCriteriaParam request parameter Map
     */
    @SneakyThrows
    public void buildCriteriaParam(QueryWrapper<T> wrapper, QueryCriteriaParam<T> queryCriteriaParam) {
        String paramName = queryCriteriaParam.getName();
        Object value = queryCriteriaParam.getValue();
        Object value2 = queryCriteriaParam.getValue2();
        String operation = queryCriteriaParam.getOperation();

        //paramName必须是build VO的属性
        //ParamName must be an attribute of build VO, Ignore apply operation!!
        if (!isApplySqlOperator(paramName, operation) && !buildHelper.checkEntityAttribute(paramName)) {
            return;
        }

        BuildUtils.checkSqlInjection(paramName, value);

        //and 和 or字段特殊处理
        //Special handling of 'and' and 'or' fields
        switch (paramName.toLowerCase()) {
            case QueryCriteriaConstants.OR_OPERATOR:
                if (!Objects.isNull(value)) {
                    List<QueryCriteriaParam<T>> queryChildCriteriaParams = (List<QueryCriteriaParam<T>>) value;
                    wrapper.or(orWrapper -> queryChildCriteriaParams.forEach(param -> buildCriteriaParam(orWrapper, param)));
                }
                break;
            case QueryCriteriaConstants.AND_OPERATOR:
                if (!Objects.isNull(value)) {
                    List<QueryCriteriaParam<T>> queryChildCriteriaParams = (List<QueryCriteriaParam<T>>) value;
                    wrapper.and(andWrapper -> queryChildCriteriaParams.forEach(param -> buildCriteriaParam(andWrapper, param)));
                }
                break;
            default:
                if (StringUtils.isNotBlank(operation)) {
                    buildCriteriaAtParam(wrapper, paramName, operation, value, value2);
                } else {
                    String paramColumnName = buildHelper.getColumn(paramName);
                    if (StringUtils.isBlank(paramColumnName)) {
                        return;
                    }
                    if (Objects.isNull(value)) {
                        wrapper.isNull(paramColumnName);
                    } else {
                        wrapper.eq(paramColumnName, value);
                    }
                }
        }
    }

    /**
     * 构建带@符号的查询条件，并拼装到Mybatis的QueryWrapper中
     * Build query conditions with @ sign and assemble them into the QueryWrapper of Mybatis
     *
     * @param wrapper   query wrapper
     * @param paramName parameter name
     * @param value     parameter value
     * @param operation parameter name @operation
     */
    private void buildCriteriaAtParam(QueryWrapper<T> wrapper, String paramName, String operation, Object value, Object value2) {

        if (isApplySqlOperator(paramName, operation)) {
            Object[] values = BuildUtils.loadValueArrays(value);
            if (values.length > 1) {
                wrapper.apply(values[0].toString(), Arrays.copyOfRange(values, 1, values.length));
            } else {
                wrapper.apply(values[0].toString());
            }
            return;
        }

        String paramColumnName = buildHelper.getColumn(paramName);
        if (StringUtils.isBlank(paramColumnName)) {
            return;
        }
        switch (operation) {
            case QueryCriteriaConstants.EQ_OPERATOR: {
                if (Objects.isNull(value)) {
                    wrapper.isNull(paramColumnName);
                } else {
                    wrapper.eq(paramColumnName, value);
                }
                break;
            }
            case QueryCriteriaConstants.NE_OPERATOR: {
                if (Objects.isNull(value)) {
                    wrapper.isNotNull(paramColumnName);
                } else {
                    wrapper.ne(paramColumnName, value);
                }
                break;
            }
            case QueryCriteriaConstants.GE_OPERATOR: {
                wrapper.ge(paramColumnName, value);
                break;
            }
            case QueryCriteriaConstants.GT_OPERATOR: {
                wrapper.gt(paramColumnName, value);
                break;
            }
            case QueryCriteriaConstants.LE_OPERATOR: {
                wrapper.le(paramColumnName, value);
                break;
            }
            case QueryCriteriaConstants.LT_OPERATOR: {
                wrapper.lt(paramColumnName, value);
                break;
            }
            case QueryCriteriaConstants.BETWEEN_OPERATOR: {
                if (Objects.isNull(value2)) {
                    String[] values = StringUtils.split(value.toString(), QueryCriteriaConstants.FIELD_DELIMITER);
                    if (values.length < 1) {
                        throw new QueryCriteriaException("Between values should be two");
                    }
                    wrapper.between(paramColumnName, values[0], values[1]);
                } else {
                    wrapper.between(paramColumnName, value, value2);
                }
                break;
            }
            case QueryCriteriaConstants.LIKE_OPERATOR: {
                wrapper.like(paramColumnName, value);
                break;
            }
            case QueryCriteriaConstants.NOT_LIKE_OPERATOR: {
                wrapper.notLike(paramColumnName, value);
                break;
            }
            case QueryCriteriaConstants.START_WITH_OPERATOR: {
                wrapper.likeRight(paramColumnName, value);
                break;
            }
            case QueryCriteriaConstants.END_WITH_OPERATOR: {
                wrapper.likeLeft(paramColumnName, value);
                break;
            }
            case QueryCriteriaConstants.IN_OPERATOR: {
                wrapper.in(paramColumnName, BuildUtils.loadValueArrays(value));
                break;
            }
            case QueryCriteriaConstants.NOT_IN_OPERATOR: {
                wrapper.notIn(paramColumnName, BuildUtils.loadValueArrays(value));
                break;
            }
            case QueryCriteriaConstants.IN_SQL_OPERATOR: {
                wrapper.inSql(paramColumnName, value.toString());
                break;
            }
            case QueryCriteriaConstants.NOT_IN_SQL_OPERATOR: {
                wrapper.notInSql(paramColumnName, value.toString());
                break;
            }

            default:
                break;
        }
    }

    /**
     * Judge whether it is an apply operation
     *
     * @param paramName parameter
     * @param operation operation
     * @return true/false
     */
    private boolean isApplySqlOperator(String paramName, String operation) {
        if (StringUtils.isNotBlank(operation)) {
            return operation.equalsIgnoreCase(QueryCriteriaConstants.APPLY_SQL_OPERATOR);
        }
        return paramName.equalsIgnoreCase(QueryCriteriaConstants.APPLY_SQL_OPERATOR);
    }
}
