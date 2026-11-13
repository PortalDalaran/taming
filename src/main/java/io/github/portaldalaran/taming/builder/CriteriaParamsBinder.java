package io.github.portaldalaran.taming.builder;


import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.BuildUtils;
import io.github.portaldalaran.taming.utils.QueryConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.format.annotation.DateTimeFormat;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * assemble QueryCriteria form CriteriaVO
 */
@Slf4j
public class CriteriaParamsBinder<V extends QueryCriteria<T>, T> {
    protected V criteriaVO;
    protected BuildHelper<T> buildHelper;
    protected List<QueryCriteriaParam<T>> queryCriteriaParams;

    public CriteriaParamsBinder(V criteriaVO, BuildHelper<T> buildHelper) {
        this.criteriaVO = criteriaVO;
        this.buildHelper = buildHelper;
    }

    /**
     * 把查询实体中，字段值不为空的拼装到QueryCriteria里
     * Assemble the non empty field values in the query entity into QueryCriteria
     *
     * @return
     */
    public V assembleCriteriaParamsByEntityValue() {
        queryCriteriaParams = criteriaVO.getCriteriaParams();
        if (Objects.isNull(queryCriteriaParams)) {
            queryCriteriaParams = new ArrayList<>();
        }

        Map<String, QueryCriteriaParam<T>> paramsByName = indexParamsByName(queryCriteriaParams);

        //between dateField
        assembleDateFieldValue(paramsByName);
        //如果是其它between字段把逗号解析（必须恰好两段）
        for (QueryCriteriaParam<T> criteriaParam : queryCriteriaParams) {
            if (criteriaParam.getOperation() == null
                    || !criteriaParam.getOperation().equalsIgnoreCase(QueryConstants.BETWEEN)) {
                continue;
            }
            if (criteriaParam.getValues() != null && criteriaParam.getValues().length >= 2) {
                continue;
            }
            if (criteriaParam.getValue() == null) {
                continue;
            }
            String value = criteriaParam.getValue().toString();
            if (!value.contains(QueryConstants.FIELD_DELIMITER)) {
                throw new QueryCriteriaException(
                        "Between expects exactly two comma-separated values: " + value);
            }
            String[] tempValue = value.split(QueryConstants.FIELD_DELIMITER, -1);
            if (tempValue.length != 2
                    || StringUtils.isBlank(tempValue[0])
                    || StringUtils.isBlank(tempValue[1])) {
                throw new QueryCriteriaException(
                        "Between expects exactly two comma-separated values: " + value);
            }
            criteriaParam.setValue(tempValue[0].trim(), tempValue[1].trim());
        }
        BeanWrapper beanWrapper = new BeanWrapperImpl(criteriaVO);
        Set<String> propertyNames = Arrays.stream(beanWrapper.getPropertyDescriptors())
                .map(PropertyDescriptor::getName)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        //过滤掉列表
        List<Field> entityFields = buildHelper.getBuildEntityFields().stream()
                .filter(field -> !Collection.class.isAssignableFrom(field.getType())
                        && !Map.class.isAssignableFrom(field.getType()))
                .collect(Collectors.toList());
        for (Field field : entityFields) {
            // pageNo/pageSize 等在 PageCriteria 上有默认值，不能当实体列条件
            if (BuildUtils.isCommonField(field.getName())) {
                continue;
            }
            if (!propertyNames.contains(field.getName().toLowerCase())) {
                continue;
            }
            Object srcValue = beanWrapper.getPropertyValue(field.getName());
            if (Objects.isNull(srcValue)) {
                continue;
            }
            QueryCriteriaParam<T> queryCriteriaParam = paramsByName.get(field.getName().toLowerCase());
            //如果已经存在，则覆盖原来的值（多值算子如 bet 不覆盖，避免破坏 between）
            //If it already exists, overwrite the original value (skip multi-value ops like bet)
            if (Objects.nonNull(queryCriteriaParam)) {
                if (isMultiValueOperation(queryCriteriaParam.getOperation())
                        || (queryCriteriaParam.getValues() != null && queryCriteriaParam.getValues().length > 1)) {
                    continue;
                }
                queryCriteriaParam.setValue(srcValue);
            } else {
                QueryCriteriaParam<T> created =
                        new QueryCriteriaParam<>(field.getName(), QueryConstants.EQ, srcValue);
                queryCriteriaParams.add(created);
                paramsByName.put(field.getName().toLowerCase(), created);
            }
        }
        this.buildHelper.setQueryCriteriaParams(queryCriteriaParams);
        return criteriaVO;
    }


    private void assembleDateFieldValue(Map<String, QueryCriteriaParam<T>> paramsByName) {
        //把日期的字段单独拿出来转换
        List<Field> dateFields = buildHelper.getBuildEntityFields().stream().filter(field -> field.getType() == LocalDate.class
                || field.getType() == LocalDateTime.class
                || field.getType() == Date.class).collect(Collectors.toList());

        // if date between
        for (Field dateField : dateFields) {
            QueryCriteriaParam<T> criteriaParam = paramsByName.get(dateField.getName().toLowerCase());
            if (Objects.nonNull(criteriaParam) && criteriaParam.getValue() instanceof String) {
                String[] dateValues = criteriaParam.getValue().toString().split(QueryConstants.FIELD_DELIMITER);
                try {
                    Object[] values = Stream.of(dateValues).map(obj -> {
                        if (LocalDate.class == dateField.getType()) {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(getAnnotationDatePattern(dateField, "yyyy-MM-dd"));
                            return LocalDate.parse(obj, formatter);
                        } else if (LocalDateTime.class == dateField.getType()) {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(getAnnotationDatePattern(dateField, "yyyy-MM-dd HH:mm:ss"));
                            return LocalDateTime.parse(obj, formatter);
                        } else {
                            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(getAnnotationDatePattern(dateField, "yyyy-MM-dd HH:mm:ss"));
                            try {
                                return simpleDateFormat.parse(obj);
                            } catch (ParseException e) {
                                throw new QueryCriteriaException("Invalid date value: " + obj, e);
                            }
                        }
                    }).toArray();
                    criteriaParam.setValue(values);
                } catch (DateTimeParseException e) {
                    throw new QueryCriteriaException("Invalid date value for field: " + dateField.getName(), e);
                }
            }
        }
    }

    private Map<String, QueryCriteriaParam<T>> indexParamsByName(List<QueryCriteriaParam<T>> params) {
        Map<String, QueryCriteriaParam<T>> index = new HashMap<>(params.size() * 2);
        for (QueryCriteriaParam<T> param : params) {
            if (param != null && param.getName() != null) {
                index.putIfAbsent(param.getName().toLowerCase(), param);
            }
        }
        return index;
    }

    private boolean isMultiValueOperation(String operation) {
        return operation != null && (QueryConstants.BETWEEN.equalsIgnoreCase(operation)
                || QueryConstants.IN.equalsIgnoreCase(operation)
                || QueryConstants.NOT_IN.equalsIgnoreCase(operation)
                || QueryConstants.APPLY_SQL.equalsIgnoreCase(operation));
    }


    /**
     * 取field注解上的json格式化日期pattern
     * Take the json formatted date pattern on the field annotation
     * Only Jackson and Spring
     *
     * @param dateField      Date or LocalDateTime LocalDate Field
     * @param defaultPattern default or null
     * @return pattern
     */
    private String getAnnotationDatePattern(Field dateField, String defaultPattern) {
        String pattern = "yyyy-MM-dd HH:mm:ss";
        if (StringUtils.isNotBlank(defaultPattern)) {
            pattern = defaultPattern;
        }

        JsonFormat jsonFormatAnnotation = dateField.getAnnotation(JsonFormat.class);
        DateTimeFormat dateTimeFormatAnnotation = dateField.getAnnotation(DateTimeFormat.class);

        if (Objects.nonNull(jsonFormatAnnotation) && StringUtils.isNotBlank(jsonFormatAnnotation.pattern())) {
            pattern = jsonFormatAnnotation.pattern();
        } else if (Objects.nonNull(dateTimeFormatAnnotation) && StringUtils.isNotBlank(dateTimeFormatAnnotation.pattern())) {
            pattern = dateTimeFormatAnnotation.pattern();
        }
        return pattern;
    }
}
