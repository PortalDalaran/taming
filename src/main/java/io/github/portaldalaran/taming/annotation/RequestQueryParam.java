package io.github.portaldalaran.taming.annotation;

import java.lang.annotation.*;

/**
 * spring mvc controller input parameters annotation
 *
 * @author aohee
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestQueryParam {
    String value() default "";

    /**
     * 是否启用参数解密。
     * 为 true 时，需在 {@link io.github.portaldalaran.taming.core.QueryCriteriaMethodArgumentResolver}
     * 中注入 {@link io.github.portaldalaran.taming.core.BodyDecryptor} 实现。
     */
    boolean encrypted() default false;
}
