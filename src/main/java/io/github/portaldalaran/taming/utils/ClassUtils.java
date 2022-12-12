package io.github.portaldalaran.taming.utils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class ClassUtils {
    public static <T> Class<T> getEntity(Class clazz) {
        Type type = clazz.getGenericSuperclass();
        Class<T> result = null;
        if (type instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) type;
            result = (Class<T>) pType.getActualTypeArguments()[0];
        }
        return result;
    }
}
