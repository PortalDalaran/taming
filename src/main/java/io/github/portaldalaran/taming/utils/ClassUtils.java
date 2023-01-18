package io.github.portaldalaran.taming.utils;

import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ClassUtils {

    private static final Map<Class<?>, List<Field>> declaredFieldsCache = new ConcurrentReferenceHashMap<>(256);
    public static <T> Class<T> getEntity(Class clazz) {
        Type type = clazz.getGenericSuperclass();
        Class<T> result = null;
        if (type instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) type;
            result = (Class<T>) pType.getActualTypeArguments()[0];
        }
        return result;
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

}
