package io.github.portaldalaran.taming.utils;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.*;

@Slf4j
public class BuildUtils {

    private static final Map<Class<?>, List<Field>> declaredFieldsCache = new ConcurrentReferenceHashMap<>(256);

    /**
     * 返回Mysql字段
     * return database column name
     *
     * @param field field name
     * @param clazz entity class
     * @return database column name
     */
    public static String getColumn(String field, Class clazz) {
        Map<String, ColumnCache> columns = LambdaUtils.getColumnMap(clazz);
        if (columns.containsKey(field.toUpperCase())) {
            return columns.get(field.toUpperCase()).getColumn();
        } else {
            log.warn(MessageFormat.format("query field `{0}` not in entity column <{1}>", field, clazz.getName()));
            return null;
        }
    }

    /**
     * 取所有的field
     *
     * @param clazz
     * @return
     */
    public static List<Field> getAllDeclaredFields(Class<?> clazz) {
        Assert.notNull(clazz, "Class must not be null");

        List<Field> queryFields = declaredFieldsCache.get(clazz);
        if (Objects.nonNull(queryFields) && queryFields.size() > 0) {
            return queryFields;
        }

        Class<?> searchType = clazz;
        queryFields = new ArrayList<>();
        List<String> fieldNames = new ArrayList<>();
        while (Object.class != searchType && searchType != null) {
            Field[] fields = searchType.getDeclaredFields();
            for (Field field : fields) {
                if (!fieldNames.contains(field.getName())) {
                    fieldNames.add(field.getName());
                    queryFields.add(field);
                }
            }
            searchType = searchType.getSuperclass();
        }
        declaredFieldsCache.put(clazz, queryFields);

        return queryFields;
    }


    /**
     * 把以逗号分割的字符串转化为数据
     * Convert comma separated strings to data
     *
     * @param value parameter value
     * @return list<>
     */
    public static Object[] loadValueArrays(Object value) {
        if (Collection.class.isAssignableFrom(value.getClass())) {
            return ((Collection<?>) value).toArray();
        }
        String[] inValues = StringUtils.split(value.toString(), QueryCriteriaConstants.FIELD_DELIMITER);

        return inValues;
    }

    public static void checkSqlInjection(String paramName, Object value) {
        if (SqlUtils.checkSqlInjection(paramName)) {
            throw new QueryCriteriaException("Query parameter SQL injection verification failed");
        }
        if (value != null && SqlUtils.checkSqlInjection(value.toString())) {
            throw new QueryCriteriaException("Query parameter SQL injection verification failed");
        }
    }
}
