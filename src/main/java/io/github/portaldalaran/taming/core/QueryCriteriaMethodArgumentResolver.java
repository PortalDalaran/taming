package io.github.portaldalaran.taming.core;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.portaldalaran.taming.annotation.RequestQueryParam;
import io.github.portaldalaran.taming.utils.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author aohee@163.com
 */
public class QueryCriteriaMethodArgumentResolver implements HandlerMethodArgumentResolver, Ordered {

    /**
     * 解析Content-Type为application/x-www-form-urlencoded的默认解析器是ServletModelAttributeMethodProcessor
     * <p>
     * The default resolver for resolving Content Type as application/x-www-form-urlencoded is ServletModelAttributeMethodProcessor
     */
    private final QueryCriteriaModelAttributeMethodProcessor queryCriteriaModelAttributeMethodProcessor;

    @Nullable
    private final BodyDecryptor decryptor;

    public QueryCriteriaMethodArgumentResolver() {
        this.queryCriteriaModelAttributeMethodProcessor = new QueryCriteriaModelAttributeMethodProcessor(true);
        this.decryptor = null;
    }

    public QueryCriteriaMethodArgumentResolver(BodyDecryptor decryptor) {
        this.queryCriteriaModelAttributeMethodProcessor = new QueryCriteriaModelAttributeMethodProcessor(true);
        this.decryptor = decryptor;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        RequestQueryParam criteriaPram = parameter.getParameterAnnotation(RequestQueryParam.class);
        return criteriaPram != null;
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Object resolveArgument(MethodParameter methodParameter, ModelAndViewContainer modelAndViewContainer, NativeWebRequest nativeWebRequest, WebDataBinderFactory webDataBinderFactory) throws Exception {
        HttpServletRequest request = nativeWebRequest.getNativeRequest(HttpServletRequest.class);

        if (request == null) {
            throw new QueryCriteriaException("request must not be null");
        }

        RequestQueryParam ann = methodParameter.getParameterAnnotation(RequestQueryParam.class);
        if (ann != null && ann.encrypted()) {
            if (decryptor == null) {
                throw new QueryCriteriaException(
                        "@RequestQueryParam(encrypted = true) requires a BodyDecryptor bean");
            }
            Map<String, String[]> decryptedParams = isJsonBody(request)
                    ? resolveEncryptedBody(request)
                    : resolveEncryptedValues(request);
            HttpServletRequest wrapper = new DecryptedRequestWrapper(request, decryptedParams);
            nativeWebRequest = new ServletWebRequest(wrapper);
        }

        return queryCriteriaModelAttributeMethodProcessor.resolveArgument(
                methodParameter, modelAndViewContainer, nativeWebRequest, webDataBinderFactory);
    }

    /**
     * 判断是否为 JSON body 请求
     */
    private boolean isJsonBody(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE);
    }

    /**
     * 整体 body 加密模式：读取 body → 解密 → 解析 JSON → 转换为参数 Map
     */
    private Map<String, String[]> resolveEncryptedBody(HttpServletRequest request) throws IOException {
        byte[] bodyBytes = StreamUtils.copyToByteArray(request.getInputStream());
        String encryptedBody = new String(bodyBytes, StandardCharsets.UTF_8);
        String decryptedJson = decryptor.decrypt(encryptedBody);
        if (decryptedJson == null) {
            throw new QueryCriteriaException("BodyDecryptor returned null");
        }

        Map<String, Object> jsonMap = JsonUtils.parseObject(decryptedJson, new TypeReference<Map<String, Object>>() {});
        return toStringArrayMap(jsonMap);
    }

    /**
     * 逐值加密模式：枚举请求参数 → 逐项解密 → 组装参数 Map
     */
    private Map<String, String[]> resolveEncryptedValues(HttpServletRequest request) {
        Map<String, String[]> result = new HashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            String[] values = request.getParameterValues(name);
            if (values == null) {
                result.put(name, null);
            } else {
                String[] decrypted = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    decrypted[i] = decryptor.decrypt(values[i]);
                }
                result.put(name, decrypted);
            }
        }
        return result;
    }

    /**
     * 将 JSON 解析出的 Map&lt;String, Object&gt; 转换为 Map&lt;String, String[]&gt;。
     * Map / 复杂对象序列化为 JSON 字符串，供 DataBinder 的 or/and 嵌套解析使用。
     */
    private Map<String, String[]> toStringArrayMap(Map<String, Object> source) {
        if (source == null) {
            return new HashMap<>();
        }
        Map<String, String[]> result = new HashMap<>(source.size());
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object val = entry.getValue();
            if (val == null) {
                result.put(entry.getKey(), new String[]{});
            } else if (val instanceof Map) {
                result.put(entry.getKey(), new String[]{JsonUtils.toJsonString(val)});
            } else if (val instanceof Iterable && !(val instanceof CharSequence)) {
                List<String> list = new ArrayList<>();
                boolean complex = false;
                for (Object item : (Iterable<?>) val) {
                    if (item instanceof Map || (item instanceof Iterable && !(item instanceof CharSequence))) {
                        complex = true;
                    }
                    list.add(item == null ? null : (item instanceof Map || item instanceof Iterable
                            ? JsonUtils.toJsonString(item) : item.toString()));
                }
                // 纯标量数组保持多值；含对象时整段作为 JSON 数组字符串，便于 applySql 等单参数场景
                if (complex) {
                    result.put(entry.getKey(), new String[]{JsonUtils.toJsonString(val)});
                } else {
                    result.put(entry.getKey(), list.toArray(new String[0]));
                }
            } else {
                result.put(entry.getKey(), new String[]{val.toString()});
            }
        }
        return result;
    }
}
