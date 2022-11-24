package io.github.portaldalaran.taming.mybatisplus;

import com.alibaba.fastjson.annotation.JSONField;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import io.github.portaldalaran.talons.annotation.JoinColumn;
import io.github.portaldalaran.talons.core.TalonsHelper;
import io.github.portaldalaran.talons.exception.TalonsException;
import io.github.portaldalaran.talons.meta.AssociationFieldInfo;
import io.github.portaldalaran.talons.meta.AssociationQueryField;
import io.github.portaldalaran.talons.meta.AssociationTableInfo;
import io.github.portaldalaran.talons.meta.AssociationType;
import io.github.portaldalaran.taming.utils.QueryCriteriaConstants;
import io.github.portaldalaran.taming.utils.SqlUtils;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.pojo.SelectAssociationFields;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.format.annotation.DateTimeFormat;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author aohee@163.com
 */
public class QueryCriteriaWrapperBuilder<T> {

    private List<AssociationQueryField> associationQueryFields = new ArrayList<>();

    private QueryWrapper<T> queryWrapper;
    private Class<T> modelClass;
    private List<String> buildEntityDeclaredFieldNames;

    public QueryCriteriaWrapperBuilder() {
        this.queryWrapper = new QueryWrapper<T>();
        this.modelClass = getEntity();
        if (Objects.isNull(this.modelClass)) {
            throw new QueryCriteriaException("Annotation ParameterizedType is null");
        }
        queryWrapper.setEntityClass(modelClass);
    }

    public QueryCriteriaWrapperBuilder(QueryWrapper<T> wrapper) {
        this.queryWrapper = wrapper;
        this.modelClass = getEntity();
        if (Objects.isNull(this.modelClass)) {
            throw new QueryCriteriaException("Annotation ParameterizedType is null");
        }
        queryWrapper.setEntityClass(modelClass);
    }

    private Class<T> getEntity() {
        Type type = getClass().getGenericSuperclass();
        Class<T> result = null;
        if (type instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) type;
            result = (Class<T>) pType.getActualTypeArguments()[0];
        }
        return result;
    }

    public QueryWrapper<T> getQueryWrapper() {
        return queryWrapper;
    }

    /**
     * 返回关联字段
     * association entity query select parameter
     * user.name,user.sex => select name,sex from user
     *
     * @return
     */
    public List<AssociationQueryField> getAssociationQueryFields() {
        return associationQueryFields;
    }

    private String getColumn(String field) {
        return getColumn(field, this.modelClass);
    }

    private String getColumn(String field, Class clazz) {
        Map<String, ColumnCache> columns = LambdaUtils.getColumnMap(clazz);
        if (columns.containsKey(field.toUpperCase())) {
            return columns.get(field.toUpperCase()).getColumn();
        } else {
            String msg = MessageFormat.format("the field `{0}` is not in entity. entity class <{1}>", field, clazz.getName());
            throw new QueryCriteriaException(msg);
        }
    }

    public <V extends QueryCriteria> boolean build(V criteriaVO) {
        buildEntityDeclaredFieldNames = Arrays.stream(criteriaVO.getClass().getDeclaredFields()).map(Field::getName).collect(Collectors.toList());
        List<QueryCriteriaParam> queryCriteriaParams = criteriaVO.getCriteriaParams();
        if (Objects.isNull(queryCriteriaParams)) {
            queryCriteriaParams = Lists.newArrayList();
        }
        assembleCriteriaParamsByEntityValue(criteriaVO, queryCriteriaParams);

        List<String> queryFields = buildFields(criteriaVO.getFields(), criteriaVO.getSelectAssociationFields());
        buildGroupBy(criteriaVO.getGroupBy(), queryFields);
        buildHaving(criteriaVO.getHaving());
        buildOrderBy(criteriaVO.getOrderBy());

        //处理where条件
        queryCriteriaParams.forEach(queryCriteriaParam -> buildCriteriaParam(queryWrapper, queryCriteriaParam));

        if (!queryFields.isEmpty()) {
            queryWrapper.select(String.join(QueryCriteriaConstants.FIELD_DELIMITER, queryFields));
        }
        return true;
    }

    /**
     * 把查询实体中，字段值不为空的拼装到QueryCriteria里
     * Assemble the non empty field values in the query entity into QueryCriteria
     *
     * @param criteriaVO          criteria query entity
     * @param queryCriteriaParams entity query parameters
     * @param <V>                 entity extends QueryCriteria or PageCriteria
     */
    @SneakyThrows
    private <V extends QueryCriteria> void assembleCriteriaParamsByEntityValue(V criteriaVO, List<QueryCriteriaParam> queryCriteriaParams) {
        //between dateField
        assembleDateFieldValue(criteriaVO, queryCriteriaParams);

        BeanWrapper beanWrapper = new BeanWrapperImpl(criteriaVO);
        PropertyDescriptor[] pds = beanWrapper.getPropertyDescriptors();

        for (String fieldName : buildEntityDeclaredFieldNames) {
            boolean isFieldName = Arrays.stream(pds).anyMatch(pd -> pd.getName().equalsIgnoreCase(fieldName));
            //如果BeanWrapper中的字段在 field中
            if (isFieldName) {
                Object srcValue = beanWrapper.getPropertyValue(fieldName);
                if (Objects.nonNull(srcValue)) {
                    QueryCriteriaParam queryCriteriaParam = queryCriteriaParams.stream().filter(params -> params.getName().equalsIgnoreCase(fieldName)).findFirst().orElse(null);
                    //如果已经存在，则覆盖原来的值
                    //If it already exists, overwrite the original value
                    if (Objects.nonNull(queryCriteriaParam)) {
                        queryCriteriaParam.setValue(srcValue);
                        queryCriteriaParam.setValue2(null);
                    } else {
                        queryCriteriaParams.add(new QueryCriteriaParam(fieldName, QueryCriteriaConstants.EQ_OPERATOR, srcValue, null));
                    }
                }
            }
        }

    }

    private static <V extends QueryCriteria> void assembleDateFieldValue(V criteriaVO, List<QueryCriteriaParam> queryCriteriaParams) throws ParseException {
        //把日期的字段单独拿出来转换
        List<Field> dateFields = Arrays.stream(criteriaVO.getClass().getDeclaredFields()).filter(field -> field.getType() == LocalDate.class
                || field.getType() == LocalDateTime.class
                || field.getType() == Date.class).collect(Collectors.toList());

        // if date between
        for (Field dateField : dateFields) {
            QueryCriteriaParam criteriaParam = queryCriteriaParams.stream().filter(param -> param.getName().equalsIgnoreCase(dateField.getName())).findFirst().orElse(null);
            if (Objects.nonNull(criteriaParam) && criteriaParam.getValue() instanceof String) {
                String dateValue = criteriaParam.getValue().toString();
                if (dateValue.indexOf(QueryCriteriaConstants.FIELD_DELIMITER) > 0) {
                    String[] tempValues = dateValue.split(QueryCriteriaConstants.FIELD_DELIMITER);
                    criteriaParam.setValue(tempValues[0]);
                    criteriaParam.setValue2(tempValues[1]);
                }

                if (LocalDate.class == dateField.getType()) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(getAnnotationDatePattern(dateField, "yyyy-MM-dd"));
                    criteriaParam.setValue(LocalDate.parse(((String) criteriaParam.getValue()).trim(), formatter));
                    if (Objects.nonNull(criteriaParam.getValue2())) {
                        criteriaParam.setValue2(LocalDate.parse(criteriaParam.getValue2().toString(), formatter));
                    }
                } else if (LocalDateTime.class == dateField.getType()) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(getAnnotationDatePattern(dateField, "yyyy-MM-dd HH:mm:ss"));

                    criteriaParam.setValue(LocalDateTime.parse(criteriaParam.getValue().toString(), formatter));
                    if (Objects.nonNull(criteriaParam.getValue2())) {
                        criteriaParam.setValue2(LocalDateTime.parse(criteriaParam.getValue2().toString(), formatter));
                    }
                } else {
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(getAnnotationDatePattern(dateField, "yyyy-MM-dd HH:mm:ss"));

                    criteriaParam.setValue(simpleDateFormat.parse(criteriaParam.getValue().toString()));
                    if (Objects.nonNull(criteriaParam.getValue2())) {
                        criteriaParam.setValue2(simpleDateFormat.parse(criteriaParam.getValue2().toString()));
                    }
                }
            }
        }
    }

    /**
     * 取field注解上的json格式化日期pattern
     * Take the json formatted date pattern on the field annotation
     * Only Jackson and Fastjson
     *
     * @param dateField      Date or LocalDateTime LocalDate Field
     * @param defaultPattern default or null
     * @return pattern
     */
    private static String getAnnotationDatePattern(Field dateField, String defaultPattern) {
        String pattern = "yyyy-MM-dd HH:mm:ss";
        if (StringUtils.isNotBlank(defaultPattern)) {
            pattern = defaultPattern;
        }

        JsonFormat jsonFormatAnnotation = dateField.getAnnotation(JsonFormat.class);
        DateTimeFormat dateTimeFormatAnnotation = dateField.getAnnotation(DateTimeFormat.class);
        JSONField jsonFieldAnnotation = dateField.getAnnotation(JSONField.class);
        com.alibaba.fastjson2.annotation.JSONField jsonField2Annotation = dateField.getAnnotation(com.alibaba.fastjson2.annotation.JSONField.class);

        if (Objects.nonNull(jsonFormatAnnotation) && StringUtils.isNotBlank(jsonFormatAnnotation.pattern())) {
            pattern = jsonFormatAnnotation.pattern();
        } else if (Objects.nonNull(dateTimeFormatAnnotation) && StringUtils.isNotBlank(dateTimeFormatAnnotation.pattern())) {
            pattern = dateTimeFormatAnnotation.pattern();
        } else if (Objects.nonNull(jsonFieldAnnotation) && StringUtils.isNotBlank(jsonFieldAnnotation.format())) {
            pattern = jsonFieldAnnotation.format();
        } else if (Objects.nonNull(jsonField2Annotation) && StringUtils.isNotBlank(jsonField2Annotation.format())) {
            pattern = jsonField2Annotation.format();
        }
        return pattern;
    }


    /**
     * 构建返回字段，如果有传fields值
     * Build the result fields,and input the fields value if any
     *
     * @param fieldsParams            paramName=fields value
     * @param selectAssociationFields paramName.con
     * @return list<string>
     */
    private List<String> buildFields(String fieldsParams, List<SelectAssociationFields> selectAssociationFields) {
        if (Objects.isNull(fieldsParams)) {
            return new ArrayList<>();
        }

        if (SqlUtils.checkSqlInjection(fieldsParams)) {
            throw new QueryCriteriaException("Query parameter SQL injection verification failed");
        }
        AssociationTableInfo<T> rsTableInfo = TalonsHelper.init(modelClass);
        List<AssociationFieldInfo> rsTableInfoAnnotations = rsTableInfo.getAnnotations();

        List<String> queryFields = new ArrayList<>();
        for (SelectAssociationFields selectAssField : selectAssociationFields) {
            if (SqlUtils.checkSqlInjection(selectAssField.getEntityName())) {
                throw new TalonsException("Query parameter SQL injection verification failed");
            }
            //通过Annotations找到与关联表对应的字段名称
            //Find the field name corresponding to the associated table through Annotations
            AssociationFieldInfo rsFieldInfo = rsTableInfoAnnotations.stream()
                    .filter(rsf -> rsf.getName().equals(selectAssField.getEntityName()))
                    .findFirst().orElse(null);

            if (Objects.isNull(rsFieldInfo)) {
                continue;
            }
            if (rsFieldInfo.getAssociationType() == AssociationType.MANYTOONE) {
                String m2oId = selectAssField.getEntityName() + "Id";
                //改为不判断，直接加上，在后边有去掉重复的字段
                //Add directly, and then there are fields to remove duplicates
                queryFields.add(getColumn(m2oId));
            }
            //如果关联对象有多个关系字段，则要加入到查询的fields中
            //If the associated object has multiple relation fields, it should be added to the fields of the query
            List<JoinColumn> joinColumns = rsFieldInfo.getJoinColumns();
            joinColumns.forEach(jc -> queryFields.add(getColumn(jc.name())));

            AssociationQueryField assQueryField = new AssociationQueryField();
            assQueryField.setTableName(selectAssField.getEntityName());
            assQueryField.setParameters(Joiner.on(",").join(selectAssField.getFieldNames()));
            associationQueryFields.add(assQueryField);
        }

        String[] inputFieldList = fieldsParams.split(QueryCriteriaConstants.FIELD_DELIMITER);
        for (String inputField : inputFieldList) {
            //ex: count(name)/sum(name)/min(name)
            if (inputField.contains("(")) {
                String calcFun = inputField.substring(0, inputField.indexOf("(")).trim();
                String calcField = inputField.substring(inputField.indexOf("(") + 1, inputField.indexOf(")")).trim();
                calcField = getColumn(calcField);
                if (!queryFields.contains(calcField)) {
                    queryFields.add(calcField);
                }
                queryFields.add(MessageFormat.format("{0}({1}) as {1}_{0}", calcFun, calcField));
            } else {
                queryFields.add(getColumn(inputField));
            }
        }
        return queryFields.stream().distinct().collect(Collectors.toList());
    }

    private void buildGroupBy(String groupByParams, List<String> queryFields) {
        if (Objects.isNull(groupByParams)) {
            return;
        }
        if (SqlUtils.checkSqlInjection(groupByParams)) {
            throw new QueryCriteriaException("Query parameter SQL injection verification failed");
        }
        //"分组  ex:\"{groupBy:\"id,name\"}\" --->SQL: group by id,name"
        List<String> groupByColumns = Lists.newArrayList();
        //如果用了groupBy在fields里自动加上 groupBy字段，groupBy字段Count
        //If groupBy is used, the groupBy field is automatically added to fields, and the groupBy field is Count
        String[] groupBySplitParams = groupByParams.split(QueryCriteriaConstants.FIELD_DELIMITER);
        for (String groupByParam : groupBySplitParams) {
            groupByParam = getColumn(groupByParam);

            //paramName必须是build VO的属性
            //ParamName must be an attribute of build VO
            if (!buildEntityDeclaredFieldNames.contains(groupByParam)) {
                continue;
            }

            if (!queryFields.contains(groupByParam)) {
                queryFields.add(groupByParam);
            }
            //count(field) as field_count
            String tempFun = MessageFormat.format("count({0}) as {0}_count", groupByParam);
            if (!queryFields.contains(tempFun)) {
                queryFields.add(tempFun);
            }
            groupByColumns.add(groupByParam);
        }
        //转换为数据库字段后，重新拼装
        //After converting to database fields, reassemble
        queryWrapper.groupBy(Joiner.on(",").join(groupByColumns));
    }

    private void buildOrderBy(String orderBy) {
        if (Objects.isNull(orderBy)) {
            return;
        }
        if (SqlUtils.checkSqlInjection(orderBy)) {
            throw new QueryCriteriaException("Query parameter SQL injection verification failed");
        }
        String[] orders = orderBy.split(QueryCriteriaConstants.FIELD_DELIMITER);
        for (String order : orders) {
            String[] tempOrder = order.split(QueryCriteriaConstants.OPTION_DELIMITER);

            //paramName必须是build VO的属性
            //ParamName must be an attribute of build VO
            if (!buildEntityDeclaredFieldNames.contains(tempOrder[0])) {
                continue;
            }

            if (tempOrder[1].equalsIgnoreCase(QueryCriteriaConstants.DESC_OPERATOR)) {
                queryWrapper.orderByDesc(getColumn(tempOrder[0]));
            } else {
                queryWrapper.orderByAsc(getColumn(tempOrder[0]));
            }
        }
    }

    private void buildHaving(String havingParams) {
        if (Objects.isNull(havingParams)) {
            return;
        }
        if (SqlUtils.checkSqlInjection(havingParams)) {
            throw new QueryCriteriaException("Query parameter SQL injection verification failed");
        }
        //"分组条件(SQL)  ex:\"{having:\"sum(age)>18\"}\" --->SQL: having sum(age)>18"
        List<String> havingColumns = Lists.newArrayList();

        String[] havingList = havingParams.split(QueryCriteriaConstants.FIELD_DELIMITER);
        for (String havingStr : havingList) {
            //有括号说明是统计
            //Parentheses indicate statistics
            if (havingStr.indexOf("(") > 1) {
                //如果是统计就要把中间的字段拿 来转换
                //For statistics, the fields in the middle should be converted
                String changStr = havingStr.substring(havingStr.indexOf("(") + 1, havingStr.indexOf(")")).trim();

                //paramName必须是build VO的属性
                //ParamName must be an attribute of build VO
                if (!buildEntityDeclaredFieldNames.contains(changStr)) {
                    continue;
                }
                havingStr = havingStr.replaceAll(changStr, getColumn(changStr));
            } else {
                String changStr = "";
                //没有括号，则根据判断符号（>\<\>=\in\like）来判断
                //If there is no bracket, it is judged according to the judgment symbol (><>= in like)
                if (havingStr.indexOf(">") > 1) {
                    changStr = havingStr.substring(0, havingStr.indexOf(">")).trim();
                } else if (havingStr.indexOf("<") > 1) {
                    changStr = havingStr.substring(0, havingStr.indexOf("<")).trim();
                } else if (havingStr.indexOf("!") > 1) {
                    changStr = havingStr.substring(0, havingStr.indexOf("!")).trim();
                } else if (havingStr.indexOf("=") > 1) {
                    changStr = havingStr.substring(0, havingStr.indexOf("=")).trim();
                } else if (havingStr.toLowerCase().indexOf("in") > 1) {
                    changStr = havingStr.substring(0, havingStr.toLowerCase().indexOf("in")).trim();
                } else if (havingStr.toLowerCase().indexOf("like") > 1) {
                    changStr = havingStr.substring(0, havingStr.toLowerCase().indexOf("like")).trim();
                }

                //paramName必须是build VO的属性
                //ParamName must be an attribute of build VO
                if (!buildEntityDeclaredFieldNames.contains(changStr)) {
                    continue;
                }

                havingStr = havingStr.replaceAll(changStr, getColumn(changStr));
            }
            havingColumns.add(havingStr);
        }
        queryWrapper.having(havingParams);
    }

    /**
     * 处理where条件，拼装到Mybatis的QueryWrapper中
     * Build where condition and assemble it into the QueryWrapper of Mybatis
     *
     * @param wrapper            queryWrapper
     * @param queryCriteriaParam request parameter Map
     */
    @SneakyThrows
    private void buildCriteriaParam(QueryWrapper<T> wrapper, QueryCriteriaParam queryCriteriaParam) {
        String paramName = queryCriteriaParam.getName();
        Object value = queryCriteriaParam.getValue();
        Object value2 = queryCriteriaParam.getValue2();
        String operation = queryCriteriaParam.getOperation();

        //paramName必须是build VO的属性
        //ParamName must be an attribute of build VO
        if (!buildEntityDeclaredFieldNames.contains(paramName)) {
            return;
        }

        if (SqlUtils.checkSqlInjection(paramName)) {
            throw new QueryCriteriaException("Query parameter SQL injection verification failed");
        }
        if (value != null && SqlUtils.checkSqlInjection(value.toString())) {
            throw new QueryCriteriaException("Query parameter SQL injection verification failed");
        }

        //and 和 or字段特殊处理
        //Special handling of 'and' and 'or' fields
        switch (paramName.toLowerCase()) {
            case QueryCriteriaConstants.OR_OPERATOR:
                if (!Objects.isNull(value)) {
                    List<QueryCriteriaParam> queryChildCriteriaParams = (List<QueryCriteriaParam>) value;
                    wrapper.or(orWrapper -> queryChildCriteriaParams.forEach(param -> buildCriteriaParam(orWrapper, param)));
                }
                break;
            case QueryCriteriaConstants.AND_OPERATOR:
                if (!Objects.isNull(value)) {
                    List<QueryCriteriaParam> queryChildCriteriaParams = (List<QueryCriteriaParam>) value;
                    wrapper.and(andWrapper -> queryChildCriteriaParams.forEach(param -> buildCriteriaParam(andWrapper, param)));
                }
                break;
            default:
                if (StringUtils.isNotBlank(operation)) {
                    buildCriteriaAtParam(wrapper, paramName, operation, value, value2);
                } else {
                    String paramColumnName = getColumn(paramName);
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
        String paramColumnName = getColumn(paramName);
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
                wrapper.in(paramColumnName, loadValueList(value));
                break;
            }
            case QueryCriteriaConstants.NOT_IN_OPERATOR: {
                wrapper.notIn(paramColumnName, loadValueList(value));
                break;
            }
            default:
                break;
        }
    }

    /**
     * 把以逗号分割的字符串转化为数据
     * Convert comma separated strings to data
     *
     * @param value parameter value
     * @return list<>
     */
    private List<Object> loadValueList(Object value) {
        String[] inValues = StringUtils.split(value.toString(), QueryCriteriaConstants.FIELD_DELIMITER);
        List<Object> intList = new ArrayList<>();
        try {
            for (String inValue : inValues) {
                intList.add(Integer.parseInt(inValue));
            }
        } catch (Exception e) {
            intList = Arrays.asList(inValues);
        }
        return intList;
    }
}
