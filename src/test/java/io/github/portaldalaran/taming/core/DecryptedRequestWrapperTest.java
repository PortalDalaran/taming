package io.github.portaldalaran.taming.core;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 解密参数包装器的四个读取方法与防御性拷贝。
 */
class DecryptedRequestWrapperTest {

    @Test
    void exposesDecryptedParameters() {
        MockHttpServletRequest original = new MockHttpServletRequest("GET", "/users");
        original.addParameter("name", "cipher");

        Map<String, String[]> decrypted = new LinkedHashMap<>();
        decrypted.put("name@like", new String[]{"wang"});
        decrypted.put("age@ge", new String[]{"18", "20"});
        decrypted.put("empty", null);

        DecryptedRequestWrapper wrapper = new DecryptedRequestWrapper(original, decrypted);

        assertEquals(3, wrapper.getParameterMap().size());
        assertEquals("wang", wrapper.getParameter("name@like"));
        assertArrayEquals(new String[]{"18", "20"}, wrapper.getParameterValues("age@ge"));
        assertNull(wrapper.getParameter("empty"));
        assertNull(wrapper.getParameterValues("empty"));
        assertNull(wrapper.getParameter("missing"));
        assertNull(wrapper.getParameterValues("missing"));

        Enumeration<String> names = wrapper.getParameterNames();
        assertTrue(names.hasMoreElements());
        assertEquals("name@like", names.nextElement());
        // 原始请求参数不应被泄漏
        assertNull(wrapper.getParameter("name"));
        // 其余属性透传原始请求
        assertEquals("GET", wrapper.getMethod());
    }

    @Test
    void defensiveCopies_isolateFromCallerMutations() {
        DecryptedRequestWrapper wrapper = new DecryptedRequestWrapper(
                new MockHttpServletRequest(), Map.of("name", new String[]{"wang"}));

        String[] values = wrapper.getParameterValues("name");
        values[0] = "tampered";
        assertEquals("wang", wrapper.getParameter("name"));

        String[] mapped = wrapper.getParameterMap().get("name");
        mapped[0] = "tampered";
        assertEquals("wang", wrapper.getParameter("name"));
        assertArrayEquals(new String[]{"wang"}, wrapper.getParameterMap().get("name"));
        assertThrows(UnsupportedOperationException.class,
                () -> wrapper.getParameterMap().put("x", new String[]{"y"}));
    }

    @Test
    void nullParamMap_yieldsEmptyView() {
        DecryptedRequestWrapper wrapper =
                new DecryptedRequestWrapper(new MockHttpServletRequest(), null);
        assertTrue(wrapper.getParameterMap().isEmpty());
        assertFalse(wrapper.getParameterNames().hasMoreElements());
        assertNull(wrapper.getParameter("name"));
    }
}
