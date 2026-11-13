package io.github.portaldalaran.taming.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.BuildUtils;
import io.github.portaldalaran.taming.utils.QueryConstants;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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

    /**
     * or/and/nested 的 value 必须是 List&lt;QueryCriteriaParam&gt;，避免错误结构导致 ClassCastException。
     */
    @SuppressWarnings("unchecked")
    private List<QueryCriteriaParam<T>> castChildCriteriaParams(String groupName, Object value) {
        if (!(value instanceof List<?> list)) {
            throw new QueryCriteriaException(
                    groupName + " value must be a list of query criteria, got: "
                            + (value == null ? "null" : value.getClass().getName()));
        }
        for (Object item : list) {
            if (!(item instanceof QueryCriteriaParam<?>)) {
                throw new QueryCriteriaException(
                        groupName + " children must be QueryCriteriaParam, got: "
                                + (item == null ? "null" : item.getClass().getName()));
            }
        }
        return (List<QueryCriteriaParam<T>>) list;
    }

    private void prepareCheckParamName(List<QueryCriteriaParam<T>> list) {
        for (QueryCriteriaParam<T> queryCriteriaParam : list) {
            prepareCheckParamName(queryCriteriaParam);
        }
    }

    private void prepareCheckParamName(QueryCriteriaParam<T> queryCriteriaParam) {
        String paramName = getParamName(queryCriteriaParam);
        String operation = queryCriteriaParam.getOperation();

        // applySql 的 value 是 SQL 片段或 applySql[n] 下标参数，不能当实体属性递归校验
        if (isApplySqlOperator(paramName, operation)) {
            BuildUtils.checkSqlInjection(paramName, operation, queryCriteriaParam.getValues());
            return;
        }

        //paramName必须是build VO的属性
        //ParamName must be an attribute of build VO, Ignore apply operation!!
        if (!buildHelper.checkEntityAttribute(paramName)) {
            throw new QueryCriteriaException("Invalid query attribute: " + paramName);
        }

        BuildUtils.checkSqlInjection(paramName, operation, queryCriteriaParam.getValues());

        //如果value是List<QueryCriteriaParam<T>> 则一直下挖（or/and/nested）
        Object nestedValue = queryCriteriaParam.getValue();
        if (nestedValue instanceof List<?> tempList
                && !tempList.isEmpty()
                && tempList.get(0) instanceof QueryCriteriaParam<?>) {
            List<QueryCriteriaParam<T>> queryChildCriteriaParams =
                    castChildCriteriaParams(paramName, nestedValue);
            for (QueryCriteriaParam<T> child : queryChildCriteriaParams) {
                prepareCheckParamName(child);
            }
        }
    }

    /**
     * 处理where条件，拼装到Mybatis的QueryWrapper中
     * Build where condition and assemble it into the QueryWrapper of Mybatis
     *
     * @param wrapper            queryWrapper
     * @param queryCriteriaParam request parameter Map
     */
    public Boolean buildCriteriaParam(QueryWrapper<T> wrapper, QueryCriteriaParam<T> queryCriteriaParam) {
        String paramName = getParamName(queryCriteriaParam);
        Object value = queryCriteriaParam.getValue();
        String operation = queryCriteriaParam.getOperation();

        if (StringUtils.isBlank(paramName)) {
            throw new QueryCriteriaException("Query attribute name must not be blank");
        }

        // pageNo/pageSize/orderBy 等保留字段不是表列，直接跳过（兼容误入 criteriaParams 的情况）
        if (BuildUtils.isCommonField(paramName)) {
            return true;
        }

        //paramName必须是build VO的属性
        //ParamName must be an attribute of build VO, Ignore apply operation!!
        if (!isApplySqlOperator(paramName, operation) && !buildHelper.checkEntityAttribute(paramName)) {
            throw new QueryCriteriaException("Invalid query attribute: " + paramName);
        }

        BuildUtils.checkSqlInjection(paramName, operation, queryCriteriaParam.getValues());

        //and 和 or字段特殊处理
        //Special handling of 'and' and 'or' fields
        switch (paramName.toLowerCase()) {
            case QueryConstants.NESTED:
                if (!Objects.isNull(value)) {
                    List<QueryCriteriaParam<T>> queryChildCriteriaParams = castChildCriteriaParams(paramName, value);
                    prepareCheckParamName(queryChildCriteriaParams);
                    wrapper.nested(nWrapper -> queryChildCriteriaParams.forEach(param -> buildCriteriaParam(nWrapper, param)));
                }
                break;
            case QueryConstants.OR:
                if (!Objects.isNull(value)) {
                    List<QueryCriteriaParam<T>> queryChildCriteriaParams = castChildCriteriaParams(paramName, value);
                    prepareCheckParamName(queryChildCriteriaParams);
                    wrapper.or(orWrapper -> queryChildCriteriaParams.forEach(param -> buildCriteriaParam(orWrapper, param)));
                }
                break;
            case QueryConstants.AND:
                if (!Objects.isNull(value)) {
                    List<QueryCriteriaParam<T>> queryChildCriteriaParams = castChildCriteriaParams(paramName, value);
                    prepareCheckParamName(queryChildCriteriaParams);
                    wrapper.and(andWrapper -> queryChildCriteriaParams.forEach(param -> buildCriteriaParam(andWrapper, param)));
                }
                break;
            default:
                if (StringUtils.isNotBlank(operation)) {
                    buildCriteriaAtParam(wrapper, paramName, operation, queryCriteriaParam.getValues());
                } else {
                    String paramColumnName = buildHelper.getColumn(paramName);
                    if (StringUtils.isBlank(paramColumnName)) {
                        throw new QueryCriteriaException("Cannot resolve column for attribute: " + paramName);
                    }
                    if (Objects.isNull(value)) {
                        wrapper.isNull(paramColumnName);
                    } else {
                        wrapper.eq(paramColumnName, value);
                    }
                }
        }
        return true;
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
            Object[] applyValues = resolveApplyValues(values);
            if (applyValues.length == 0 || applyValues[0] == null || StringUtils.isBlank(applyValues[0].toString())) {
                throw new QueryCriteriaException("applySql value must not be blank");
            }
            if (applyValues.length > 1) {
                wrapper.apply(applyValues[0].toString(), Arrays.copyOfRange(applyValues, 1, applyValues.length));
            } else {
                wrapper.apply(applyValues[0].toString());
            }
            return;
        }
        String normalizedOp = normalizeOperation(operation);
        if (normalizedOp == null) {
            throw new QueryCriteriaException("Unsupported query operation: " + operation);
        }
        if (values == null || values.length == 0) {
            if (QueryConstants.NULL.equals(normalizedOp)) {
                String col = requireColumn(paramName);
                wrapper.isNull(col);
                return;
            }
            if (QueryConstants.NOT_NULL.equals(normalizedOp)) {
                String col = requireColumn(paramName);
                wrapper.isNotNull(col);
                return;
            }
            throw new QueryCriteriaException("Query value must not be null for operation: " + operation);
        }
        Object value = values[0];
        String paramColumnName = requireColumn(paramName);
        switch (normalizedOp) {
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
                requireValue(value, normalizedOp);
                wrapper.ge(paramColumnName, value);
                break;
            }
            case QueryConstants.GT: {
                requireValue(value, normalizedOp);
                wrapper.gt(paramColumnName, value);
                break;
            }
            case QueryConstants.LE: {
                requireValue(value, normalizedOp);
                wrapper.le(paramColumnName, value);
                break;
            }
            case QueryConstants.LT: {
                requireValue(value, normalizedOp);
                wrapper.lt(paramColumnName, value);
                break;
            }
            case QueryConstants.BETWEEN: {
                if (values.length < 2 || values[0] == null || values[1] == null) {
                    throw new QueryCriteriaException("Between values should be two");
                }
                wrapper.between(paramColumnName, values[0], values[1]);
                break;
            }
            case QueryConstants.LIKE: {
                requireValue(value, normalizedOp);
                wrapper.like(paramColumnName, value);
                break;
            }
            case QueryConstants.NOT_LIKE: {
                requireValue(value, normalizedOp);
                wrapper.notLike(paramColumnName, value);
                break;
            }
            case QueryConstants.START_WITH: {
                requireValue(value, normalizedOp);
                wrapper.likeRight(paramColumnName, value);
                break;
            }
            case QueryConstants.END_WITH: {
                requireValue(value, normalizedOp);
                wrapper.likeLeft(paramColumnName, value);
                break;
            }
            case QueryConstants.IN: {
                requireValue(value, normalizedOp);
                if (values.length > 1) {
                    wrapper.in(paramColumnName, values);
                } else {
                    wrapper.in(paramColumnName, BuildUtils.loadValueArrays(value));
                }
                break;
            }
            case QueryConstants.NOT_IN: {
                requireValue(value, normalizedOp);
                if (values.length > 1) {
                    wrapper.notIn(paramColumnName, values);
                } else {
                    wrapper.notIn(paramColumnName, BuildUtils.loadValueArrays(value));
                }
                break;
            }
            case QueryConstants.IN_SQL: {
                requireValue(value, normalizedOp);
                wrapper.inSql(paramColumnName, value.toString());
                break;
            }
            case QueryConstants.NOT_IN_SQL: {
                requireValue(value, normalizedOp);
                wrapper.notInSql(paramColumnName, value.toString());
                break;
            }
            case QueryConstants.NULL: {
                wrapper.isNull(paramColumnName);
                break;
            }
            case QueryConstants.NOT_NULL: {
                wrapper.isNotNull(paramColumnName);
                break;
            }
            default:
                throw new QueryCriteriaException("Unsupported query operation: " + operation);
        }
    }

    /**
     * 展开 applySql 参数：支持多值、逗号分隔，以及 applySql[0]/applySql[1] 产生的 List&lt;QueryCriteriaParam&gt;。
     */
    @SuppressWarnings("unchecked")
    private Object[] resolveApplyValues(Object... values) {
        if (values == null || values.length == 0) {
            return new Object[0];
        }
        if (values.length > 1) {
            return unwrapApplyElements(Arrays.asList(values));
        }
        Object first = values[0];
        if (first instanceof Collection) {
            Collection<?> coll = (Collection<?>) first;
            if (!coll.isEmpty() && coll.iterator().next() instanceof QueryCriteriaParam<?>) {
                List<QueryCriteriaParam<?>> params = new ArrayList<>((Collection<QueryCriteriaParam<?>>) coll);
                params.sort(Comparator.comparingInt(p -> parseApplyIndex(p.getName())));
                Object[] resolved = new Object[params.size()];
                for (int i = 0; i < params.size(); i++) {
                    resolved[i] = params.get(i).getValue();
                }
                return resolved;
            }
            return coll.toArray();
        }
        return BuildUtils.loadValueArrays(first);
    }

    private Object[] unwrapApplyElements(List<?> elements) {
        Object[] resolved = new Object[elements.size()];
        for (int i = 0; i < elements.size(); i++) {
            Object el = elements.get(i);
            resolved[i] = el instanceof QueryCriteriaParam<?> ? ((QueryCriteriaParam<?>) el).getValue() : el;
        }
        return resolved;
    }

    private int parseApplyIndex(String name) {
        if (name == null || name.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private String requireColumn(String paramName) {
        String paramColumnName = buildHelper.getColumn(paramName);
        if (StringUtils.isBlank(paramColumnName)) {
            throw new QueryCriteriaException("Cannot resolve column for attribute: " + paramName);
        }
        return paramColumnName;
    }

    private void requireValue(Object value, String operation) {
        if (value == null) {
            throw new QueryCriteriaException("Query value must not be null for operation: " + operation);
        }
    }

    private static final String[] SUPPORTED_OPERATIONS = {
            QueryConstants.EQ, QueryConstants.NE, QueryConstants.GE, QueryConstants.GT,
            QueryConstants.LE, QueryConstants.LT, QueryConstants.BETWEEN, QueryConstants.LIKE,
            QueryConstants.NOT_LIKE, QueryConstants.START_WITH, QueryConstants.END_WITH,
            QueryConstants.IN, QueryConstants.NOT_IN, QueryConstants.IN_SQL, QueryConstants.NOT_IN_SQL,
            QueryConstants.NULL, QueryConstants.NOT_NULL, QueryConstants.APPLY_SQL
    };

    /**
     * 将请求中的算子归一为规范常量（忽略大小写，保留 notLike/startWith 等驼峰形态）。
     */
    private String normalizeOperation(String operation) {
        if (StringUtils.isBlank(operation)) {
            return null;
        }
        String trimmed = operation.trim();
        for (String known : SUPPORTED_OPERATIONS) {
            if (known.equalsIgnoreCase(trimmed)) {
                return known;
            }
        }
        return null;
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
        return StringUtils.isNotBlank(paramName) && paramName.equalsIgnoreCase(QueryConstants.APPLY_SQL);
    }
}
