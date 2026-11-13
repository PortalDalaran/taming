package io.github.portaldalaran.taming.utils;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.*;

@Slf4j
public class BuildUtils {

    private static final Map<Class<?>, List<Field>> declaredFieldsCache = new ConcurrentReferenceHashMap<>(256);
    private static final Map<Class<?>, Map<String, String>> columnNameCache = new ConcurrentReferenceHashMap<>(256);
    /**
     * 分页/排序/投影等框架保留字段（小写），不是实体表列，组装 WHERE 时应跳过。
     */
    private static final Set<String> COMMON_FIELDS = Set.of(
            "sortorder", "orderby", "pageno", "pagesize", "groupby", "having", "fields");

    /**
     * 是否为分页、排序、投影等保留查询字段（非实体列）。
     *
     * @param field 属性名，允许大小写混用
     * @return true 表示应跳过，不作为 WHERE 条件
     */
    public static boolean isCommonField(String field) {
        return field != null && COMMON_FIELDS.contains(field.toLowerCase());
    }

    /**
     * 返回Mysql字段
     * return database column name
     *
     * @param field field name
     * @param clazz entity class
     * @return database column name
     */
    @SuppressWarnings("rawtypes")
    public static String getColumn(String field, Class clazz) {
        if (field == null || clazz == null) {
            return null;
        }
        Map<String, String> fieldColumns = columnNameCache.get(clazz);
        if (fieldColumns == null) {
            fieldColumns = loadColumnMap(clazz);
            // 仅缓存已初始化的列映射，避免 MP TableInfo 未就绪时把空结果永久缓存
            if (fieldColumns != null) {
                columnNameCache.put(clazz, fieldColumns);
            }
        }
        if (fieldColumns != null) {
            String column = fieldColumns.get(field.toUpperCase());
            if (column != null) {
                return column;
            }
        }
        //ignore common fields
        if (!isCommonField(field)) {
            log.warn(MessageFormat.format("query field `{0}` not in entity column <{1}>", field, clazz.getName()));
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map<String, String> loadColumnMap(Class clazz) {
        Map<String, ColumnCache> columns = LambdaUtils.getColumnMap(clazz);
        if (columns == null || columns.isEmpty()) {
            return null;
        }
        Map<String, String> mapped = new HashMap<>(columns.size());
        for (Map.Entry<String, ColumnCache> entry : columns.entrySet()) {
            mapped.put(entry.getKey(), entry.getValue().getColumn());
        }
        return mapped;
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
        if (value == null) {
            throw new QueryCriteriaException("Query value must not be null");
        }
        if (Collection.class.isAssignableFrom(value.getClass())) {
            return ((Collection<?>) value).toArray();
        }
        String[] inValues = StringUtils.split(value.toString(), QueryConstants.FIELD_DELIMITER);
        return inValues == null ? new Object[0] : inValues;
    }

    /**
     * 校验参数名；仅对会拼接 SQL 片段的算子校验 value（applySql / inSql / notInSql）。
     * 普通条件走 MyBatis-Plus 参数绑定，不对 value 做引号级扫描，避免误杀合法输入。
     */
    public static void checkSqlInjection(String paramName, String operation, Object... values) {
        if (StringUtils.isNotBlank(paramName) && SqlUtils.checkSqlInjection(paramName)) {
            throw new QueryCriteriaException("Query parameter SQL injection verification failed");
        }
        if (!isSqlSplicingOperation(paramName, operation) || values == null) {
            return;
        }
        for (Object value : values) {
            checkSqlFragmentDeep(value);
        }
    }

    /**
     * 递归校验 SQL 片段；展开 Collection / QueryCriteriaParam，避免只扫到 List.toString()。
     */
    private static void checkSqlFragmentDeep(Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof QueryCriteriaParam) {
            Object[] nested = ((QueryCriteriaParam<?>) value).getValues();
            if (nested != null) {
                for (Object v : nested) {
                    checkSqlFragmentDeep(v);
                }
            }
            return;
        }
        if (value instanceof Collection) {
            for (Object v : (Collection<?>) value) {
                checkSqlFragmentDeep(v);
            }
            return;
        }
        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                checkSqlFragmentDeep(Array.get(value, i));
            }
            return;
        }
        if (SqlUtils.checkSqlFragment(value.toString())) {
            throw new QueryCriteriaException("Query parameter SQL injection verification failed");
        }
    }

    /**
     * @deprecated 请使用 {@link #checkSqlInjection(String, String, Object...)}
     */
    @Deprecated
    public static void checkSqlInjection(String paramName, Object... values) {
        checkSqlInjection(paramName, null, values);
    }

    private static boolean isSqlSplicingOperation(String paramName, String operation) {
        if (StringUtils.isNotBlank(operation)) {
            return QueryConstants.APPLY_SQL.equalsIgnoreCase(operation)
                    || QueryConstants.IN_SQL.equalsIgnoreCase(operation)
                    || QueryConstants.NOT_IN_SQL.equalsIgnoreCase(operation);
        }
        return StringUtils.isNotBlank(paramName) && QueryConstants.APPLY_SQL.equalsIgnoreCase(paramName);
    }

    public static <T> String getFieldName(SFunction<T, ?> column) {
        SerializedLambda serializedLambda = getSerializedLambda(column);
        String methodName = serializedLambda.getImplMethodName();
        if (methodName.startsWith("get")) {
            methodName = methodName.substring("get".length());
        }
        return CharSequenceUtils.lowerFirst(methodName);
    }

    @SneakyThrows
    private static <T> SerializedLambda getSerializedLambda(SFunction<T, ?> column) {
        Method method = column.getClass().getDeclaredMethod("writeReplace");
        boolean isAccessible = method.isAccessible();
        method.setAccessible(true);
        SerializedLambda serializedLambda = (SerializedLambda) method.invoke(column);
        method.setAccessible(isAccessible);
        return serializedLambda;
    }
}
