package io.github.portaldalaran.taming.core;

import io.github.portaldalaran.taming.annotation.RequestQueryParam;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.JsonUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptedResolverTest {

    @Data
    @EqualsAndHashCode(callSuper = true)
    static class UserCriteria extends QueryCriteria<Object> {
        private String name;
        private Integer age;
    }

    @SuppressWarnings("unused")
    void sample(@RequestQueryParam(encrypted = true) UserCriteria criteria) {
    }

    private final WebDataBinderFactory binderFactory =
            (webRequest, target, objectName) -> new ServletRequestDataBinder(target, objectName);

    @Test
    void encryptedWithoutDecryptor_throws() throws Exception {
        Method method = EncryptedResolverTest.class.getDeclaredMethod("sample", UserCriteria.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        QueryCriteriaMethodArgumentResolver resolver = new QueryCriteriaMethodArgumentResolver();

        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThrows(QueryCriteriaException.class,
                () -> resolver.resolveArgument(parameter, new ModelAndViewContainer(),
                        new ServletWebRequest(request), binderFactory));
    }

    @Test
    void nestedJsonMap_serializesAsJsonNotMapToString() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("id", 2);
        nested.put("age", 18);
        String json = JsonUtils.toJsonString(nested);
        assertTrue(json.contains("\"id\""));
        assertTrue(json.contains(":"));
        assertEquals(2, ((Map<?, ?>) JsonUtils.parseObject(json, Map.class)).get("id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void nestedOrJson_bindsLikeEncryptedBodyConversion() {
        // 模拟 toStringArrayMap 对嵌套 Map 序列化成 JSON 后的表单参数
        UserCriteria criteria = new UserCriteria();
        QueryCriteriaDataBinder binder = new QueryCriteriaDataBinder(criteria, "criteria");

        Map<String, Object> orMap = new HashMap<>();
        orMap.put("id", 2);
        orMap.put("age", 18);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("name@like", "wang");
        request.addParameter("or", JsonUtils.toJsonString(orMap));

        binder.bind(request);

        List<QueryCriteriaParam<?>> params =
                (List<QueryCriteriaParam<?>>) (List<?>) criteria.getCriteriaParams();
        assertEquals(2, params.size());
        assertEquals("name", params.get(0).getName());
        assertEquals("or", params.get(1).getName());
        assertTrue(params.get(1).getValue() instanceof List);
    }
}
