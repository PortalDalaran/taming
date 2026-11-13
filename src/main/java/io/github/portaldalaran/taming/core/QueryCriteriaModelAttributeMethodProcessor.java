package io.github.portaldalaran.taming.core;

import jakarta.servlet.ServletRequest;
import org.springframework.util.Assert;
import org.springframework.validation.Validator;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ServletModelAttributeMethodProcessor;

/**
 * 基于 Spring {@link ServletModelAttributeMethodProcessor}，仅覆盖绑定逻辑以支持 {@code field@operation} 语法。
 */
public class QueryCriteriaModelAttributeMethodProcessor extends ServletModelAttributeMethodProcessor {

    public QueryCriteriaModelAttributeMethodProcessor(boolean annotationNotRequired) {
        super(annotationNotRequired);
    }

    /**
     * 使用 {@link QueryCriteriaDataBinder} 替代默认 {@code ServletRequestDataBinder}。
     */
    @Override
    protected void bindRequestParameters(WebDataBinder binder, NativeWebRequest request) {
        ServletRequest servletRequest = request.getNativeRequest(ServletRequest.class);
        Assert.state(servletRequest != null, "No ServletRequest");
        QueryCriteriaDataBinder queryCriteriaDataBinder =
                new QueryCriteriaDataBinder(binder.getTarget(), binder.getObjectName());
        if (binder.getConversionService() != null) {
            queryCriteriaDataBinder.setConversionService(binder.getConversionService());
        }
        for (Validator validator : binder.getValidators()) {
            queryCriteriaDataBinder.addValidators(validator);
        }
        queryCriteriaDataBinder.bind(servletRequest);
    }
}
