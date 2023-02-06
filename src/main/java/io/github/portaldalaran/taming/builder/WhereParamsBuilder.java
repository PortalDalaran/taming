package io.github.portaldalaran.taming.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.BuildUtils;
import io.github.portaldalaran.taming.utils.QueryConstants;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * build where paramters
 *
 * @param <T>
 */
public class WhereParamsBuilder<T> {
    protected BuildHelper<T> buildHelper;

    public WhereParamsBuilder(BuildHelper<T> buildHelper) {
        this.buildHelper = buildHelper;
    }

    private String getParamName(QueryCriteriaParam<T> queryCriteriaParam) {
        String paramName = queryCriteriaParam.getName();

        if (Objects.nonNull(queryCriteriaParam.getColumn()) && StringUtils.isBlank(paramName)) {
            paramName = BuildUtils.getFieldName(queryCriteriaParam.getColumn());
        }
        return paramName;
    }

    private Boolean prepareCheckParamName(List<QueryCriteriaParam<T>> list) {
        for (QueryCriteriaParam<T> queryCriteriaParam : list) {
            boolean checked = prepareCheckParamName(queryCriteriaParam);
            if (!checked) {
                return false;
            }
        }
        return true;
    }

    private Boolean prepareCheckParamName(QueryCriteriaParam<T> queryCriteriaParam) {
        String paramName = getParamName(queryCriteriaParam);
        String operation = queryCriteriaParam.getOperation();

        //paramName必须是build VO的属性
        //ParamName must be an attribute of build VO, Ignore apply operation!!
        if (!isApplySqlOperator(paramName, operation) && !buildHelper.checkEntityAttribute(paramName)) {
            return false;
        }

        BuildUtils.checkSqlInjection(paramName, queryCriteriaParam.getValues());

        //如果value是List<QueryCriteriaParam<T>> 则一直下挖
        if (Collection.class.isAssignableFrom(queryCriteriaParam.getValue().getClass())) {
            List<?> tempList = (List<?>) queryCriteriaParam.getValue();
            if (!tempList.isEmpty() && tempList.get(0) instanceof QueryCriteriaParam<?>) {
                List<QueryCriteriaParam<T>> queryChildCriteriaParams = (List<QueryCriteriaParam<T>>) queryCriteriaParam.getValue();
                for (QueryCriteriaParam<T> child : queryChildCriteriaParams) {
                    boolean checked = prepareCheckParamName(child);
                    if (!checked) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * 处理where条件，拼装到Mybatis的QueryWrapper中
     * Build where condition and assemble it into the QueryWrapper of Mybatis
     *
     * @param wrapper            queryWrapper
     * @param queryCriteriaParam request parameter Map
     */
    @SneakyThrows
    public Boolean buildCriteriaParam(QueryWrapper<T> wrapper, QueryCriteriaParam<T> queryCriteriaParam) {
        String paramName = getParamName(queryCriteriaParam);
        Object value = queryCriteriaParam.getValue();
        String operation = queryCriteriaParam.getOperation();

        //paramName必须是build VO的属性
        //ParamName must be an attribute of build VO, Ignore apply operation!!
        if (!isApplySqlOperator(paramName, operation) && !buildHelper.checkEntityAttribute(paramName)) {
            return false;
        }

        BuildUtils.checkSqlInjection(paramName, queryCriteriaParam.getValues());

        AtomicBoolean result = new AtomicBoolean(true);
        //and 和 or字段特殊处理
        //Special handling of 'and' and 'or' fields
        switch (paramName.toLowerCase()) {
            case QueryConstants.NESTED:
                if (!Objects.isNull(value)) {
                    List<QueryCriteriaParam<T>> queryChildCriteriaParams = (List<QueryCriteriaParam<T>>) value;
                    boolean succeed = prepareCheckParamName(queryChildCriteriaParams);
                    if (succeed) {
                        wrapper.nested(nWrapper -> queryChildCriteriaParams.forEach(param -> {
                            if (Boolean.FALSE.equals(buildCriteriaParam(nWrapper, param))) {
                                result.set(false);
                            }
                        }));
                    } else {
                        result.set(false);
                    }
                }
                break;
            case QueryConstants.OR:
                if (!Objects.isNull(value)) {
                    List<QueryCriteriaParam<T>> queryChildCriteriaParams = (List<QueryCriteriaParam<T>>) value;
                    boolean succeed = prepareCheckParamName(queryChildCriteriaParams);
                    if (succeed) {
                        wrapper.or(orWrapper -> queryChildCriteriaParams.forEach(param -> {
                            if (Boolean.FALSE.equals(buildCriteriaParam(orWrapper, param))) {
                                result.set(false);
                            }
                        }));
                    } else {
                        result.set(false);
                    }
                }
                break;
            case QueryConstants.AND:
                if (!Objects.isNull(value)) {
                    List<QueryCriteriaParam<T>> queryChildCriteriaParams = (List<QueryCriteriaParam<T>>) value;
                    boolean succeed = prepareCheckParamName(queryChildCriteriaParams);
                    if (succeed) {
                        wrapper.and(andWrapper -> queryChildCriteriaParams.forEach(param -> {
                            if (Boolean.FALSE.equals(buildCriteriaParam(andWrapper, param))) {
                                result.set(false);
                            }
                        }));
                    } else {
                        result.set(false);
                    }

                }
                break;
            default:
                if (StringUtils.isNotBlank(operation)) {
                    buildCriteriaAtParam(wrapper, paramName, operation, queryCriteriaParam.getValues());
                } else {
                    String paramColumnName = buildHelper.getColumn(paramName);
                    if (StringUtils.isBlank(paramColumnName)) {
                        return false;
                    }
                    if (Objects.isNull(value)) {
                        wrapper.isNull(paramColumnName);
                    } else {
                        wrapper.eq(paramColumnName, value);
                    }
                    return true;
                }
        }
        return result.get();
    }

    /**
     * 构建带@符号的查询条件，并拼装到Mybatis的QueryWrapper中
     * Build query conditions with @ sign and assemble them into the QueryWrapper of Mybatis
     *
     * @param wrapper   query wrapper
     * @param paramName parameter name
     * @param values    parameter value
     * @param operation parameter name @operation
     */
    private void buildCriteriaAtParam(QueryWrapper<T> wrapper, String paramName, String operation, Object... values) {

        if (isApplySqlOperator(paramName, operation)) {
            Object[] applyValues = values.length > 1 ? values : BuildUtils.loadValueArrays(values);
            if (applyValues.length > 1) {
                wrapper.apply(applyValues[0].toString(), Arrays.copyOfRange(applyValues, 1, applyValues.length));
            } else {
                wrapper.apply(applyValues[0].toString());
            }
            return;
        }
        Object value = values[0];
        String paramColumnName = buildHelper.getColumn(paramName);
        if (StringUtils.isBlank(paramColumnName)) {
            return;
        }
        switch (operation) {
            case QueryConstants.EQ: {
                if (Objects.isNull(value)) {
                    wrapper.isNull(paramColumnName);
                } else {
                    wrapper.eq(paramColumnName, value);
                }
                break;
            }
            case QueryConstants.NE: {
                if (Objects.isNull(value)) {
                    wrapper.isNotNull(paramColumnName);
                } else {
                    wrapper.ne(paramColumnName, value);
                }
                break;
            }
            case QueryConstants.GE: {
                wrapper.ge(paramColumnName, value);
                break;
            }
            case QueryConstants.GT: {
                wrapper.gt(paramColumnName, value);
                break;
            }
            case QueryConstants.LE: {
                wrapper.le(paramColumnName, value);
                break;
            }
            case QueryConstants.LT: {
                wrapper.lt(paramColumnName, value);
                break;
            }
            case QueryConstants.BETWEEN: {
                if (values.length < 2) {
                    throw new QueryCriteriaException("Between values should be two");
                }
                wrapper.between(paramColumnName, values[0], values[1]);
                break;
            }
            case QueryConstants.LIKE: {
                wrapper.like(paramColumnName, value);
                break;
            }
            case QueryConstants.NOT_LIKE: {
                wrapper.notLike(paramColumnName, value);
                break;
            }
            case QueryConstants.START_WITH: {
                wrapper.likeRight(paramColumnName, value);
                break;
            }
            case QueryConstants.END_WITH: {
                wrapper.likeLeft(paramColumnName, value);
                break;
            }
            case QueryConstants.IN: {
                if (values.length > 1) {
                    wrapper.in(paramColumnName, values);
                } else {
                    wrapper.in(paramColumnName, BuildUtils.loadValueArrays(value));
                }
                break;
            }
            case QueryConstants.NOT_IN: {
                if (values.length > 1) {
                    wrapper.notIn(paramColumnName, values);
                } else {
                    wrapper.notIn(paramColumnName, BuildUtils.loadValueArrays(value));
                }
                break;
            }
            case QueryConstants.IN_SQL: {
                wrapper.inSql(paramColumnName, value.toString());
                break;
            }
            case QueryConstants.NOT_IN_SQL: {
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
            return operation.equalsIgnoreCase(QueryConstants.APPLY_SQL);
        }
        return paramName.equalsIgnoreCase(QueryConstants.APPLY_SQL);
    }
}
