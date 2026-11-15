package io.github.portaldalaran.taming.core;

import io.github.portaldalaran.taming.annotation.RequestQueryParam;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 参数解析器的注解识别、顺序、两种解密模式与异常路径。
 */
class QueryCriteriaMethodArgumentResolverTest {

    @Data
    @EqualsAndHashCode(callSuper = true)
    static class UserCriteria extends QueryCriteria<Object> {
        private String name;
        private Integer age;
    }

    @SuppressWarnings("unused")
    void encryptedSample(@RequestQueryParam(encrypted = true) UserCriteria criteria) {
    }

    @SuppressWarnings("unused")
    void plainSample(@RequestQueryParam UserCriteria criteria) {
    }

    @SuppressWarnings("unused")
    void noAnnotationSample(UserCriteria criteria) {
    }

    private final WebDataBinderFactory binderFactory = (webRequest, target, objectName) -> {
        ServletRequestDataBinder binder = new ServletRequestDataBinder(target, objectName);
        // ModelAttributeMethodProcessor 在 constructAttribute 阶段传入 null target，
        // 需要由 binder 提供目标类型才能实例化参数对象
        if (target == null) {
            binder.setTargetType(ResolvableType.forClass(UserCriteria.class));
        }
        return binder;
    };

    private MethodParameter parameter(String methodName) throws Exception {
        Method method = QueryCriteriaMethodArgumentResolverTest.class
                .getDeclaredMethod(methodName, UserCriteria.class);
        return new MethodParameter(method, 0);
    }

    private Object resolve(QueryCriteriaMethodArgumentResolver resolver,
                           MethodParameter parameter, MockHttpServletRequest request) throws Exception {
        return resolver.resolveArgument(parameter, new ModelAndViewContainer(),
                new ServletWebRequest(request), binderFactory);
    }

    @Test
    void supportsParameter_onlyForAnnotatedParameters() throws Exception {
        QueryCriteriaMethodArgumentResolver resolver = new QueryCriteriaMethodArgumentResolver();
        assertTrue(resolver.supportsParameter(parameter("encryptedSample")));
        assertTrue(resolver.supportsParameter(parameter("plainSample")));
        assertFalse(resolver.supportsParameter(parameter("noAnnotationSample")));
    }

    @Test
    void order_isJustBelowHighestPrecedence() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 1,
                new QueryCriteriaMethodArgumentResolver().getOrder());
    }

    @Test
    void nullRequest_throws() throws Exception {
        ServletWebRequest webRequest = new ServletWebRequest(new MockHttpServletRequest()) {
            @Override
            public <T> T getNativeRequest(Class<T> requiredType) {
                return null;
            }
        };
        QueryCriteriaMethodArgumentResolver resolver = new QueryCriteriaMethodArgumentResolver();
        assertThrows(QueryCriteriaException.class, () ->
                resolver.resolveArgument(parameter("plainSample"), new ModelAndViewContainer(),
                        webRequest, binderFactory));
    }

    @Test
    @SuppressWarnings("unchecked")
    void encryptedJsonBody_isDecryptedAndBound() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/users");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("CIPHER".getBytes(StandardCharsets.UTF_8));

        BodyDecryptor decryptor = cipher ->
                "{\"name@like\":\"wang\",\"age@ge\":\"18\"}";
        QueryCriteriaMethodArgumentResolver resolver = new QueryCriteriaMethodArgumentResolver(decryptor);

        UserCriteria criteria = (UserCriteria) resolve(resolver, parameter("encryptedSample"), request);
        assertNotNull(criteria.getCriteriaParams());
        assertTrue(criteria.getCriteriaParams().stream()
                .anyMatch(p -> "name".equals(p.getName()) && "wang".equals(p.getValue())));
        assertTrue(criteria.getCriteriaParams().stream()
                .anyMatch(p -> "age".equals(p.getName()) && "ge".equals(p.getOperation())));
    }

    @Test
    void encryptedFormValues_areDecryptedOneByOne() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users");
        request.addParameter("name@like", "ENC:wang");
        request.addParameter("age@ge", new String[]{"ENC:18", "ENC:20"});

        BodyDecryptor decryptor = cipher -> cipher.replaceFirst("^ENC:", "");
        QueryCriteriaMethodArgumentResolver resolver = new QueryCriteriaMethodArgumentResolver(decryptor);

        UserCriteria criteria = (UserCriteria) resolve(resolver, parameter("encryptedSample"), request);
        QueryCriteriaParam<Object> name = criteria.getCriteriaParams().stream()
                .filter(p -> "name".equals(p.getName())).findFirst().orElseThrow();
        assertEquals("wang", name.getValue());
        QueryCriteriaParam<Object> age = criteria.getCriteriaParams().stream()
                .filter(p -> "age".equals(p.getName())).findFirst().orElseThrow();
        assertInstanceOf(String[].class, age.getValue());
        assertEquals("20", ((String[]) age.getValue())[1]);
    }

    @Test
    void decryptorReturningNull_throws() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/users");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("CIPHER".getBytes(StandardCharsets.UTF_8));
        QueryCriteriaMethodArgumentResolver resolver =
                new QueryCriteriaMethodArgumentResolver(cipher -> null);

        assertThrows(QueryCriteriaException.class, () ->
                resolve(resolver, parameter("encryptedSample"), request));
    }

    @Test
    void plainParameter_bindsWithoutDecryptor() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users");
        request.addParameter("name@like", "wang");
        QueryCriteriaMethodArgumentResolver resolver = new QueryCriteriaMethodArgumentResolver();

        UserCriteria criteria = (UserCriteria) resolve(resolver, parameter("plainSample"), request);
        assertEquals(1, criteria.getCriteriaParams().size());
        assertEquals("wang", criteria.getCriteriaParams().get(0).getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void encryptedBody_handlesScalarArraysNullsAndNestedMaps() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/users");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("CIPHER".getBytes(StandardCharsets.UTF_8));

        BodyDecryptor decryptor = cipher -> "{\"name@like\":\"wang\","
                + "\"ids@in\":[1,2,3],\"empty\":null,\"or\":{\"id\":2}}";
        QueryCriteriaMethodArgumentResolver resolver = new QueryCriteriaMethodArgumentResolver(decryptor);

        UserCriteria criteria = (UserCriteria) resolve(resolver, parameter("encryptedSample"), request);
        List<QueryCriteriaParam<Object>> params = criteria.getCriteriaParams();
        assertTrue(params.stream().anyMatch(p -> "ids".equals(p.getName())
                && p.getValue() instanceof String[]));
        assertTrue(params.stream().anyMatch(p -> "empty".equals(p.getName()) && p.getValue() == null));
        QueryCriteriaParam<Object> or = params.stream()
                .filter(p -> "or".equals(p.getName())).findFirst().orElseThrow();
        assertInstanceOf(List.class, or.getValue());
        assertEquals(1, ((List<?>) or.getValue()).size());
    }

    @Test
    void encryptedWithoutDecryptorBean_throws() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users");
        QueryCriteriaMethodArgumentResolver resolver = new QueryCriteriaMethodArgumentResolver();
        assertThrows(QueryCriteriaException.class, () ->
                resolve(resolver, parameter("encryptedSample"), request));
    }
}
