package io.github.portaldalaran.taming.utils;

import io.github.portaldalaran.taming.pojo.QueryCriteria;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 一对一主从表排序器，
 * one to one Entity comparator
 *
 * @param <Q> extend QueryCriteria is request VO
 * @param <T> data type class
 * @author aohee
 */
@Slf4j
public class One2OneObjectsComparator<T, Q extends QueryCriteria> implements Comparator<T> {
    private Q queryReqVO;
    private Class<T> tClazz;

    public One2OneObjectsComparator(Q queryReqVO) {
        this.queryReqVO = queryReqVO;
        this.tClazz = ClassUtils.getEntity(getClass());
    }

    @Override
    public int compare(T o1, T o2) {
        if (Objects.isNull(this.queryReqVO)) {
            return 0;
        }
        String[] orders = queryReqVO.getOrderBy().split(QueryCriteriaConstants.FIELD_DELIMITER);
        List<Field> declareFields = Arrays.stream(tClazz.getDeclaredFields()).collect(Collectors.toList());
        List<String> fieldNames = declareFields.stream().map(Field::getName).collect(Collectors.toList());
        // 0的含义是在两个元素相同时，不交换顺序（为了排序算法的稳定性，可以使用1来代替0，不要用-1来代替0）
        int allSort = 0;
        for (String order : orders) {
            int sort = 0;
            String[] tempOrder = order.split(QueryCriteriaConstants.OPTION_DELIMITER);
            String paramName = tempOrder[0];
            //paramName必须是build VO的属性
            //ParamName must be an attribute of build VO
            if (StringUtils.isBlank(paramName) || !fieldNames.contains(paramName)) {
                continue;
            }
            Field field = declareFields.stream().filter(f -> f.getName().equalsIgnoreCase(paramName)).findFirst().orElse(null);
            try {
                assert field != null;
                field.setAccessible(true);
                if (Objects.isNull(field.get(o1))) {
                    sort = -1;
                    continue;
                }
                if (Objects.isNull(field.get(o2))) {
                    sort = 1;
                    continue;
                }
                if (field.getType() == Long.class) {
                    sort = Long.compare((Long) field.get(o1), (Long) field.get(o2));
                } else if (field.getType() == Integer.class) {
                    sort = Integer.compare((Integer) field.get(o1), (Integer) field.get(o2));
                } else if (field.getType() == Double.class) {
                    sort = Double.compare((Double) field.get(o1), (Double) field.get(o2));
                } else if (field.getType() == Float.class) {
                    sort = Float.compare((Float) field.get(o1), (Float) field.get(o2));
                } else if (field.getType() == BigDecimal.class) {
                    sort = ((BigDecimal) field.get(o1)).compareTo(((BigDecimal) field.get(o2)));
                } else if (field.getType() == Date.class) {
                    sort = ((Date) field.get(o1)).compareTo(((Date) field.get(o2)));
                } else if (field.getType() == LocalDate.class) {
                    sort = ((LocalDate) field.get(o1)).compareTo(((LocalDate) field.get(o2)));
                } else if (field.getType() == LocalDateTime.class) {
                    sort = ((LocalDateTime) field.get(o1)).compareTo(((LocalDateTime) field.get(o2)));
                } else {
                    sort = field.get(o1).toString().compareToIgnoreCase(field.get(o2).toString());
                }
                if (tempOrder[1].equalsIgnoreCase(QueryCriteriaConstants.DESC_OPERATOR)) {
                    sort = sort == 0 ? 0 : sort == -1 ? 1 : -1;
                }
                allSort = allSort == 0 ? sort : allSort;
            } catch (IllegalAccessException e) {
                log.warn("", e);
            }
        }
        return allSort;
    }
}
