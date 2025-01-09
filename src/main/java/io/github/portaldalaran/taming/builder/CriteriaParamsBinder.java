package io.github.portaldalaran.taming.builder;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.common.collect.Lists;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.QueryConstants;
import lombok.SneakyThrows;
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
    @SneakyThrows
    public V assembleCriteriaParamsByEntityValue() {
        queryCriteriaParams = criteriaVO.getCriteriaParams();
        if (Objects.isNull(queryCriteriaParams)) {
            queryCriteriaParams = Lists.newArrayList();
        }

        //between dateField
        assembleDateFieldValue();
        //如果是其它between字段把逗号解析
        List<QueryCriteriaParam<T>> bets = queryCriteriaParams.stream().filter(param -> param.getOperation().equalsIgnoreCase(QueryConstants.BETWEEN)).collect(Collectors.toList());
        for (QueryCriteriaParam<T> criteriaParam : bets) {
            String value = criteriaParam.getValue().toString();
            if (value.contains(QueryConstants.FIELD_DELIMITER)) {
                criteriaParam.setValue(Arrays.asList(value.split(QueryConstants.FIELD_DELIMITER)));
            }
        }
        BeanWrapper beanWrapper = new BeanWrapperImpl(criteriaVO);
        PropertyDescriptor[] pds = beanWrapper.getPropertyDescriptors();
        //过滤掉列表
        List<Field> entityFields = buildHelper.getBuildEntityFields().stream().filter(field -> !Collection.class.isAssignableFrom(field.getType()) && !Map.class.isAssignableFrom(field.getType())).collect(Collectors.toList());
        for (Field field : entityFields) {
            boolean isFieldName = Arrays.stream(pds).anyMatch(pd -> pd.getName().equalsIgnoreCase(field.getName()));

            //如果BeanWrapper中的字段在 field中
            if (isFieldName) {
                Object srcValue = beanWrapper.getPropertyValue(field.getName());
                if (Objects.nonNull(srcValue)) {
                    QueryCriteriaParam<T> queryCriteriaParam = queryCriteriaParams.stream().filter(params -> params.getName().equalsIgnoreCase(field.getName())).findFirst().orElse(null);
                    //如果已经存在，则覆盖原来的值
                    //If it already exists, overwrite the original value
                    if (Objects.nonNull(queryCriteriaParam)) {
                        queryCriteriaParam.setValue(srcValue, null);
                    } else {
                        queryCriteriaParams.add(new QueryCriteriaParam<>(field.getName(), QueryConstants.EQ, srcValue, null));
                    }
                }
            }
        }
        this.buildHelper.setQueryCriteriaParams(queryCriteriaParams);
        return criteriaVO;

    }


    private void assembleDateFieldValue() {
        //把日期的字段单独拿出来转换
        List<Field> dateFields = buildHelper.getBuildEntityFields().stream().filter(field -> field.getType() == LocalDate.class
                || field.getType() == LocalDateTime.class
                || field.getType() == Date.class).collect(Collectors.toList());

        // if date between
        for (Field dateField : dateFields) {
            QueryCriteriaParam<T> criteriaParam = queryCriteriaParams.stream().filter(param -> param.getName().equalsIgnoreCase(dateField.getName())).findFirst().orElse(null);
            if (Objects.nonNull(criteriaParam) && criteriaParam.getValue() instanceof String) {
                String[] dateValues = criteriaParam.getValue().toString().split(QueryConstants.FIELD_DELIMITER);
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
                            log.error("转换日期出错", e);
                            return null;
                        }
                    }
                }).toArray();
                criteriaParam.setValue(values);
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
    private String getAnnotationDatePattern(Field dateField, String defaultPattern) {
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
}
