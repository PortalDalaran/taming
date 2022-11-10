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
}
