package io.github.portaldalaran.taming.builder;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.common.collect.Lists;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.BuildUtils;
import io.github.portaldalaran.taming.utils.QueryCriteriaConstants;
import lombok.SneakyThrows;
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

/**
 * assemble QueryCriteria form CriteriaVO
 */
public class CriteriaParamsBinder<V extends QueryCriteria<T>,T> {
    private V criteriaVO;
    private BuildHelper<T> buildHelper;
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
        List<QueryCriteriaParam<T>> queryCriteriaParams = criteriaVO.getCriteriaParams();
        if (Objects.isNull(queryCriteriaParams)) {
            queryCriteriaParams = Lists.newArrayList();
        }

        //between dateField
        assembleDateFieldValue(criteriaVO, queryCriteriaParams);

        BeanWrapper beanWrapper = new BeanWrapperImpl(criteriaVO);
        PropertyDescriptor[] pds = beanWrapper.getPropertyDescriptors();
        //过滤掉列表
        List<Field> entityFields =  BuildUtils.getAllDeclaredFields(criteriaVO.getClass()).stream().filter(field -> !Collection.class.isAssignableFrom(field.getType()) && !Map.class.isAssignableFrom(field.getType())).collect(Collectors.toList());
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
                        queryCriteriaParam.setValue(srcValue);
                        queryCriteriaParam.setValue2(null);
                    } else {
                        queryCriteriaParams.add(new QueryCriteriaParam<>(field.getName(), QueryCriteriaConstants.EQ_OPERATOR, srcValue, null));
                    }
                }
            }
        }
        this.buildHelper.setQueryCriteriaParams(queryCriteriaParams);
        return criteriaVO;

    }


    private  <V extends QueryCriteria<T>,T> void assembleDateFieldValue(V criteriaVO, List<QueryCriteriaParam<T>> queryCriteriaParams) throws ParseException {
        //把日期的字段单独拿出来转换
        List<Field> dateFields =  BuildUtils.getAllDeclaredFields(criteriaVO.getClass()).stream().filter(field -> field.getType() == LocalDate.class
                || field.getType() == LocalDateTime.class
                || field.getType() == Date.class).collect(Collectors.toList());

        // if date between
        for (Field dateField : dateFields) {
            QueryCriteriaParam<T> criteriaParam = queryCriteriaParams.stream().filter(param -> param.getName().equalsIgnoreCase(dateField.getName())).findFirst().orElse(null);
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
