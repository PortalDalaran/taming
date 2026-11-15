package io.github.portaldalaran.taming.autoconfigure;

import io.github.portaldalaran.taming.core.BodyDecryptor;
import io.github.portaldalaran.taming.core.QueryCriteriaMethodArgumentResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自动配置的解析器注册、BodyDecryptor 注入与开关条件。
 */
class TamingAutoConfigurationTest {

    static class LowPriorityResolver implements HandlerMethodArgumentResolver, Ordered {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return false;
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                      NativeWebRequest request, WebDataBinderFactory factory) {
            return null;
        }

        @Override
        public int getOrder() {
            return LOWEST_PRECEDENCE;
        }
    }

    private TamingAutoConfiguration configurationWith(BodyDecryptor decryptor) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        if (decryptor != null) {
            factory.registerSingleton("bodyDecryptor", decryptor);
        }
        return new TamingAutoConfiguration(factory.getBeanProvider(BodyDecryptor.class));
    }

    @Test
    void addArgumentResolvers_registersSingleResolverWithoutDecryptor() throws Exception {
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
        configurationWith(null).addArgumentResolvers(resolvers);

        assertEquals(1, resolvers.size());
        assertInstanceOf(QueryCriteriaMethodArgumentResolver.class, resolvers.get(0));
        Field field = QueryCriteriaMethodArgumentResolver.class.getDeclaredField("decryptor");
        field.setAccessible(true);
        assertNull(field.get(resolvers.get(0)));
    }

    @Test
    void addArgumentResolvers_injectsAvailableBodyDecryptor() throws Exception {
        BodyDecryptor decryptor = cipher -> cipher;
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
        configurationWith(decryptor).addArgumentResolvers(resolvers);

        assertEquals(1, resolvers.size());
        Field field = QueryCriteriaMethodArgumentResolver.class.getDeclaredField("decryptor");
        field.setAccessible(true);
        assertNotNull(field.get(resolvers.get(0)));
    }

    @Test
    void addArgumentResolvers_sortsByOrder() {
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
        resolvers.add(new LowPriorityResolver());
        configurationWith(null).addArgumentResolvers(resolvers);

        assertEquals(2, resolvers.size());
        assertInstanceOf(QueryCriteriaMethodArgumentResolver.class, resolvers.get(0));
        assertInstanceOf(LowPriorityResolver.class, resolvers.get(1));
    }

    @Test
    void autoConfiguration_isEnabledByDefaultInServletContext() {
        assertTrue(autoConfigurationPresent());
    }

    @Test
    void autoConfiguration_canBeDisabledByProperty() {
        assertFalse(autoConfigurationPresent("taming.web.enabled=false"));
        assertTrue(autoConfigurationPresent("taming.web.enabled=true"));
    }

    private boolean autoConfigurationPresent(String... propertyValues) {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        try {
            context.setServletContext(new MockServletContext());
            if (propertyValues.length > 0) {
                Map<String, Object> properties = new HashMap<>();
                for (String property : propertyValues) {
                    String[] parts = property.split("=", 2);
                    properties.put(parts[0], parts[1]);
                }
                context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("test", properties));
            }
            context.register(TamingAutoConfiguration.class);
            context.refresh();
            return context.getBeanNamesForType(TamingAutoConfiguration.class).length > 0;
        } finally {
            context.close();
        }
    }
}
