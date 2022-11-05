package io.github.protaldalaran.taming.core;

import io.github.protaldalaran.taming.annotation.RequestQueryParam;
import org.apache.logging.log4j.util.Strings;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.HttpServletRequest;

/**
 * @author aohee@163.com
 */
@Component
public class QueryCriteriaMethodArgumentResolver implements HandlerMethodArgumentResolver, Ordered {

    /**
     * 解析Content-Type为application/x-www-form-urlencoded的默认解析器是ServletModelAttributeMethodProcessor
     *
     * The default resolver for resolving Content Type as application/x-www-form-urlencoded is ServletModelAttributeMethodProcessor
     */
    private QueryCriteriaModelAttributeMethodProcessor queryCriteriaModelAttributeMethodProcessor;

    public QueryCriteriaMethodArgumentResolver() {
        this.queryCriteriaModelAttributeMethodProcessor = new QueryCriteriaModelAttributeMethodProcessor(true);
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
            throw new RuntimeException(" request must not be null!");
        }
        String contentType = request.getContentType();

        //没有，默认用表单
        if (Strings.isBlank(contentType)) {
            return queryCriteriaModelAttributeMethodProcessor.resolveArgument(methodParameter, modelAndViewContainer, nativeWebRequest, webDataBinderFactory);
        }


        return queryCriteriaModelAttributeMethodProcessor.resolveArgument(methodParameter, modelAndViewContainer, nativeWebRequest, webDataBinderFactory);
    }


}
