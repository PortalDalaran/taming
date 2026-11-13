package io.github.portaldalaran.taming.core;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 持有解密后参数 Map 的请求包装器，覆盖四个参数读取方法，其余属性完全透传原始请求。
 *
 * @author aohee@163.com
 */
public class DecryptedRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String[]> decryptedParams;

    public DecryptedRequestWrapper(HttpServletRequest request, Map<String, String[]> decryptedParams) {
        super(request);
        Map<String, String[]> copy = new LinkedHashMap<>();
        if (decryptedParams != null) {
            for (Map.Entry<String, String[]> entry : decryptedParams.entrySet()) {
                String[] values = entry.getValue();
                copy.put(entry.getKey(), values == null ? null : values.clone());
            }
        }
        this.decryptedParams = Collections.unmodifiableMap(copy);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return decryptedParams;
    }

    @Override
    public String getParameter(String name) {
        String[] values = decryptedParams.get(name);
        return (values != null && values.length > 0) ? values[0] : null;
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = decryptedParams.get(name);
        return values == null ? null : values.clone();
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(decryptedParams.keySet());
    }
}
