package io.github.protaldalaran.taming.core;

import io.github.protaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.protaldalaran.taming.utils.QueryCriteriaConstants;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.*;
import org.springframework.validation.annotation.ValidationAnnotationUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.annotation.ModelFactory;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.multipart.support.StandardServletPartUtils;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Rewrite according to org.springframework.web.method.annotation.ModelAttributeMethodProcessor
 *
 * @author aohee@163.com
 */
public class QueryCriteriaModelAttributeMethodProcessor implements HandlerMethodArgumentResolver, HandlerMethodReturnValueHandler {
    private final boolean annotationNotRequired;

    /**
     * Class constructor.
     *
     * @param annotationNotRequired if "true", non-simple method arguments and
     *                              return values are considered model attributes with or without a
     *                              {@code @ModelAttribute} annotation
     */
    public QueryCriteriaModelAttributeMethodProcessor(boolean annotationNotRequired) {
        this.annotationNotRequired = annotationNotRequired;
    }


    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return (parameter.hasParameterAnnotation(ModelAttribute.class) ||
                (this.annotationNotRequired && !BeanUtils.isSimpleProperty(parameter.getParameterType())));
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {

        String name = ModelFactory.getNameForParameter(parameter);
        ModelAttribute ann = parameter.getParameterAnnotation(ModelAttribute.class);
        if (ann != null) {
            mavContainer.setBinding(name, ann.binding());
        }

        Object attribute = null;
        BindingResult bindingResult = null;

        if (mavContainer.containsAttribute(name)) {
            attribute = mavContainer.getModel().get(name);
        } else {
            // Create attribute instance
            try {
                attribute = createAttribute(name, parameter, binderFactory, webRequest);
            } catch (BindException ex) {
                if (isBindExceptionRequired(parameter)) {
                    // No BindingResult parameter -> fail with BindException
                    throw ex;
                }
                // Otherwise, expose null/empty value and associated BindingResult
                if (parameter.getParameterType() == Optional.class) {
                    attribute = Optional.empty();
                } else {
                    attribute = ex.getTarget();
                }
                bindingResult = ex.getBindingResult();
            }
        }

        if (bindingResult == null) {
            // Bean property binding and validation;
            // skipped in case of binding failure on construction.
            WebDataBinder binder = binderFactory.createBinder(webRequest, attribute, name);
            if (binder.getTarget() != null) {
                if (!mavContainer.isBindingDisabled(name)) {
                    bindRequestParameters(binder, webRequest);
                }
                validateIfApplicable(binder, parameter);
                if (binder.getBindingResult().hasErrors() && isBindExceptionRequired(binder, parameter)) {
                    throw new BindException(binder.getBindingResult());
                }
            }
            // Value type adaptation, also covering java.util.Optional
            if (!parameter.getParameterType().isInstance(attribute)) {
                attribute = binder.convertIfNecessary(binder.getTarget(), parameter.getParameterType(), parameter);
            }
            bindingResult = binder.getBindingResult();
        }

        // Add resolved attribute and BindingResult at the end of the model
        Map<String, Object> bindingResultModel = bindingResult.getModel();
        mavContainer.removeAttributes(bindingResultModel);
        mavContainer.addAllAttributes(bindingResultModel);

        return attribute;
    }

    /**
     * Extension point to create the model attribute if not found in the model,
     * with subsequent parameter binding through bean properties (unless suppressed).
     * <p>The default implementation typically uses the unique public no-arg constructor
     * if available but also handles a "primary constructor" approach for data classes:
     * It understands the JavaBeans {@code ConstructorProperties} annotation as well as
     * runtime-retained parameter names in the bytecode, associating request parameters
     * with constructor arguments by name. If no such constructor is found, the default
     * constructor will be used (even if not public), assuming subsequent bean property
     * bindings through setter methods.
     *
     * @param attributeName the name of the attribute (never {@code null})
     * @param parameter     the method parameter declaration
     * @param binderFactory for creating WebDataBinder instance
     * @param webRequest    the current request
     * @return the created model attribute (never {@code null})
     * @throws BindException in case of constructor argument binding failure
     * @throws Exception     in case of constructor invocation failure
     * @see #constructAttribute(Constructor, String, MethodParameter, WebDataBinderFactory, NativeWebRequest)
     * @see BeanUtils#findPrimaryConstructor(Class)
     */
    protected Object createAttribute(String attributeName, MethodParameter parameter,
                                     WebDataBinderFactory binderFactory, NativeWebRequest webRequest) throws Exception {
        //**来自ServletModelAttributeMethodProcessor
        String value = getRequestValueForAttribute(attributeName, webRequest);
        if (value != null) {
            Object attribute = createAttributeFromRequestValue(value, attributeName, parameter, binderFactory, webRequest);
            if (attribute != null) {
                return attribute;
            }
        }

        MethodParameter nestedParameter = parameter.nestedIfOptional();
        Class<?> clazz = nestedParameter.getNestedParameterType();

        Constructor<?> ctor = BeanUtils.getResolvableConstructor(clazz);
        Object attribute = constructAttribute(ctor, attributeName, parameter, binderFactory, webRequest);
        if (parameter != nestedParameter) {
            attribute = Optional.of(attribute);
        }
        return attribute;
    }

    @Nullable
    protected String getRequestValueForAttribute(String attributeName, NativeWebRequest request) {
        Map<String, String> variables = this.getUriTemplateVariables(request);
        String variableValue = variables.get(attributeName);
        if (StringUtils.hasText(variableValue)) {
            return variableValue;
        } else {
            String parameterValue = request.getParameter(attributeName);
            return StringUtils.hasText(parameterValue) ? parameterValue : null;
        }
    }

    /**
     * 返回Uri key-value
     *
     * @param request
     * @return
     */
    protected final Map<String, String> getUriTemplateVariables(NativeWebRequest request) {
        Map<String, String> variables = (Map) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, 0);
        return variables != null ? variables : Collections.emptyMap();
    }

    @Nullable
    protected Object createAttributeFromRequestValue(String sourceValue, String attributeName, MethodParameter parameter, WebDataBinderFactory binderFactory, NativeWebRequest request) throws Exception {
        DataBinder binder = binderFactory.createBinder(request, (Object) null, attributeName);
        ConversionService conversionService = binder.getConversionService();
        if (conversionService != null) {
            TypeDescriptor source = TypeDescriptor.valueOf(String.class);
            TypeDescriptor target = new TypeDescriptor(parameter);
            if (conversionService.canConvert(source, target)) {
                return binder.convertIfNecessary(sourceValue, parameter.getParameterType(), parameter);
            }
        }

        return null;
    }

    /**
     * Construct a new attribute instance with the given constructor.
     * <p>Called from
     * {@link #createAttribute(String, MethodParameter, WebDataBinderFactory, NativeWebRequest)}
     * after constructor resolution.
     *
     * @param ctor          the constructor to use
     * @param attributeName the name of the attribute (never {@code null})
     * @param binderFactory for creating WebDataBinder instance
     * @param webRequest    the current request
     * @return the created model attribute (never {@code null})
     * @throws BindException in case of constructor argument binding failure
     * @throws Exception     in case of constructor invocation failure
     * @since 5.1
     */
    protected Object constructAttribute(Constructor<?> ctor, String attributeName, MethodParameter parameter,
                                        WebDataBinderFactory binderFactory, NativeWebRequest webRequest) throws Exception {

        if (ctor.getParameterCount() == 0) {
            // A single default constructor -> clearly a standard JavaBeans arrangement.
            return BeanUtils.instantiateClass(ctor);
        }

        // A single data class constructor -> resolve constructor arguments from request parameters.
        String[] paramNames = BeanUtils.getParameterNames(ctor);
        Class<?>[] paramTypes = ctor.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        WebDataBinder binder = binderFactory.createBinder(webRequest, null, attributeName);
        String fieldDefaultPrefix = binder.getFieldDefaultPrefix();
        String fieldMarkerPrefix = binder.getFieldMarkerPrefix();
        boolean bindingFailure = false;
        Set<String> failedParams = new HashSet<>(4);

        //处理自定义查询 Process custom queries
        List<QueryCriteriaParam> criteriaParams = new ArrayList<>();
        //找到这个字段的位置，然后insert
        //Find the location of this field, and insert
        int criteriaParamsIndex = -1;
        for (int i = 0; i < paramNames.length; i++) {
            String paramSourceName = paramNames[i];
            String paramName = paramSourceName;
            Class<?> paramType = paramTypes[i];
            Object value = webRequest.getParameterValues(paramName);

            //操作类型
            String operation = "";
            //如果有操作
            //If there is operation
            if (paramSourceName.contains(QueryCriteriaConstants.OPTION_DELIMITER)) {
                String[] paramNameSplits = paramSourceName.split(QueryCriteriaConstants.OPTION_DELIMITER);
                paramName = paramNameSplits[0];
                operation = paramNameSplits[1];
            }

            // Since WebRequest#getParameter exposes a single-value parameter as an array
            // with a single element, we unwrap the single value in such cases, analogous
            // to WebExchangeDataBinder.addBindValue(Map<String, Object>, String, List<?>).
            if (ObjectUtils.isArray(value) && Array.getLength(value) == 1) {
                value = Array.get(value, 0);
            }

            if (value == null) {
                if (fieldDefaultPrefix != null) {
                    value = webRequest.getParameter(fieldDefaultPrefix + paramName);
                }
                if (value == null) {
                    if (fieldMarkerPrefix != null && webRequest.getParameter(fieldMarkerPrefix + paramName) != null) {
                        value = binder.getEmptyValue(paramType);
                    } else {
                        value = resolveConstructorArgument(paramName, paramType, webRequest);
                    }
                }
            }

            //如果操作符不为空
            //If the operator is not empty
            if (StringUtils.hasText(operation)) {
                criteriaParams.add(new QueryCriteriaParam(paramName, operation, value));
            }
            if (paramName.equalsIgnoreCase("criteriaParams")) {
                criteriaParamsIndex = i;
            }

            try {
                MethodParameter methodParam = new FieldAwareConstructorParameter(ctor, i, paramName);
                if (value == null && methodParam.isOptional()) {
                    args[i] = (methodParam.getParameterType() == Optional.class ? Optional.empty() : null);
                } else {
                    args[i] = binder.convertIfNecessary(value, paramType, methodParam);
                }
            } catch (TypeMismatchException ex) {
                ex.initPropertyName(paramName);
                args[i] = null;
                failedParams.add(paramName);
                binder.getBindingResult().recordFieldValue(paramName, paramType, value);
                binder.getBindingErrorProcessor().processPropertyAccessException(ex, binder.getBindingResult());
                bindingFailure = true;
            }
        }

        //如果有，则绑定criteriaParams
        // If yes, bind criteriaParams
        if (criteriaParamsIndex > -1) {
            MethodParameter methodParam = new FieldAwareConstructorParameter(ctor, criteriaParamsIndex, "criteriaParams");
            args[criteriaParamsIndex] = binder.convertIfNecessary(criteriaParams, ArrayList.class, methodParam);
        }

        if (bindingFailure) {
            BindingResult result = binder.getBindingResult();
            for (int i = 0; i < paramNames.length; i++) {
                String paramName = paramNames[i];
                if (!failedParams.contains(paramName)) {
                    Object value = args[i];
                    result.recordFieldValue(paramName, paramTypes[i], value);
                    validateValueIfApplicable(binder, parameter, ctor.getDeclaringClass(), paramName, value);
                }
            }
            if (!parameter.isOptional()) {
                try {
                    Object target = BeanUtils.instantiateClass(ctor, args);
                    throw new BindException(result) {
                        @Override
                        public Object getTarget() {
                            return target;
                        }
                    };
                } catch (BeanInstantiationException ex) {
                    // swallow and proceed without target instance
                }
            }
            throw new BindException(result);
        }

        return BeanUtils.instantiateClass(ctor, args);
    }

    /**
     * Extension point to bind the request to the target object.
     *
     * @param binder  the data binder instance to use for the binding
     * @param request the current request
     */
    protected void bindRequestParameters(WebDataBinder binder, NativeWebRequest request) {
        ServletRequest servletRequest = request.getNativeRequest(ServletRequest.class);
        Assert.state(servletRequest != null, "No ServletRequest");
        //重点改的这里
        //The key change is here
        QueryCriteriaDataBinder queryCriteriaDataBinder = new QueryCriteriaDataBinder(binder.getTarget(), binder.getObjectName());
        queryCriteriaDataBinder.bind(servletRequest);
    }

    @Nullable
    public Object resolveConstructorArgument(String paramName, Class<?> paramType, NativeWebRequest request)
            throws Exception {

        MultipartRequest multipartRequest = request.getNativeRequest(MultipartRequest.class);
        if (multipartRequest != null) {
            List<MultipartFile> files = multipartRequest.getFiles(paramName);
            if (!files.isEmpty()) {
                return (files.size() == 1 ? files.get(0) : files);
            }
        } else if (StringUtils.startsWithIgnoreCase(
                request.getHeader(HttpHeaders.CONTENT_TYPE), MediaType.MULTIPART_FORM_DATA_VALUE)) {
            HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);
            if (servletRequest != null && HttpMethod.POST.matches(servletRequest.getMethod())) {
                List<Part> parts = StandardServletPartUtils.getParts(servletRequest, paramName);
                if (!parts.isEmpty()) {
                    return (parts.size() == 1 ? parts.get(0) : parts);
                }
            }
        }

        //补的
        ServletRequest servletRequest = request.getNativeRequest(ServletRequest.class);
        if (servletRequest != null) {
            String attr = HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE;
            Map<String, String> uriVars = (Map) servletRequest.getAttribute(attr);
            return uriVars.get(paramName);
        } else {
            return null;
        }
    }

    /**
     * Validate the model attribute if applicable.
     * <p>The default implementation checks for {@code @javax.validation.Valid},
     * Spring's {@link org.springframework.validation.annotation.Validated},
     * and custom annotations whose name starts with "Valid".
     *
     * @param binder    the DataBinder to be used
     * @param parameter the method parameter declaration
     * @see WebDataBinder#validate(Object...)
     * @see SmartValidator#validate(Object, Errors, Object...)
     */
    protected void validateIfApplicable(WebDataBinder binder, MethodParameter parameter) {
        for (Annotation ann : parameter.getParameterAnnotations()) {
            Object[] validationHints = ValidationAnnotationUtils.determineValidationHints(ann);
            if (validationHints != null) {
                binder.validate(validationHints);
                break;
            }
        }
    }

    /**
     * Validate the specified candidate value if applicable.
     * <p>The default implementation checks for {@code @javax.validation.Valid},
     * Spring's {@link org.springframework.validation.annotation.Validated},
     * and custom annotations whose name starts with "Valid".
     *
     * @param binder     the DataBinder to be used
     * @param parameter  the method parameter declaration
     * @param targetType the target type
     * @param fieldName  the name of the field
     * @param value      the candidate value
     * @see #validateIfApplicable(WebDataBinder, MethodParameter)
     * @see SmartValidator#validateValue(Class, String, Object, Errors, Object...)
     * @since 5.1
     */
    protected void validateValueIfApplicable(WebDataBinder binder, MethodParameter parameter,
                                             Class<?> targetType, String fieldName, @Nullable Object value) {

        for (Annotation ann : parameter.getParameterAnnotations()) {
            Object[] validationHints = ValidationAnnotationUtils.determineValidationHints(ann);
            if (validationHints != null) {
                for (Validator validator : binder.getValidators()) {
                    if (validator instanceof SmartValidator) {
                        try {
                            ((SmartValidator) validator).validateValue(targetType, fieldName, value,
                                    binder.getBindingResult(), validationHints);
                        } catch (IllegalArgumentException ex) {
                            // No corresponding field on the target class...
                        }
                    }
                }
                break;
            }
        }
    }

    /**
     * Whether to raise a fatal bind exception on validation errors.
     * <p>The default implementation delegates to {@link #isBindExceptionRequired(MethodParameter)}.
     *
     * @param binder    the data binder used to perform data binding
     * @param parameter the method parameter declaration
     * @return {@code true} if the next method parameter is not of type {@link Errors}
     * @see #isBindExceptionRequired(MethodParameter)
     */
    protected boolean isBindExceptionRequired(WebDataBinder binder, MethodParameter parameter) {
        return isBindExceptionRequired(parameter);
    }

    /**
     * Whether to raise a fatal bind exception on validation errors.
     *
     * @param parameter the method parameter declaration
     * @return {@code true} if the next method parameter is not of type {@link Errors}
     * @since 5.0
     */
    protected boolean isBindExceptionRequired(MethodParameter parameter) {
        int i = parameter.getParameterIndex();
        Class<?>[] paramTypes = parameter.getExecutable().getParameterTypes();
        boolean hasBindingResult = (paramTypes.length > (i + 1) && Errors.class.isAssignableFrom(paramTypes[i + 1]));
        return !hasBindingResult;
    }

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return (returnType.hasMethodAnnotation(ModelAttribute.class) ||
                (this.annotationNotRequired && !BeanUtils.isSimpleProperty(returnType.getParameterType())));
    }

    @Override
    public void handleReturnValue(Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
        if (returnValue != null) {
            String name = ModelFactory.getNameForReturnValue(returnValue, returnType);
            mavContainer.addAttribute(name, returnValue);
        }
    }


    /**
     * {@link MethodParameter} subclass which detects field annotations as well.
     *
     * @since 5.1
     */
    public static class FieldAwareConstructorParameter extends MethodParameter {

        private final String parameterName;

        @Nullable
        private volatile Annotation[] combinedAnnotations;

        public FieldAwareConstructorParameter(Constructor<?> constructor, int parameterIndex, String parameterName) {
            super(constructor, parameterIndex);
            this.parameterName = parameterName;
        }

        @Override
        public Annotation[] getParameterAnnotations() {
            Annotation[] anns = this.combinedAnnotations;
            if (anns == null) {
                anns = super.getParameterAnnotations();
                try {
                    Field field = getDeclaringClass().getDeclaredField(this.parameterName);
                    Annotation[] fieldAnns = field.getAnnotations();
                    if (fieldAnns.length > 0) {
                        List<Annotation> merged = new ArrayList<>(anns.length + fieldAnns.length);
                        merged.addAll(Arrays.asList(anns));
                        for (Annotation fieldAnn : fieldAnns) {
                            boolean existingType = false;
                            for (Annotation ann : anns) {
                                if (ann.annotationType() == fieldAnn.annotationType()) {
                                    existingType = true;
                                    break;
                                }
                            }
                            if (!existingType) {
                                merged.add(fieldAnn);
                            }
                        }
                        anns = merged.toArray(new Annotation[0]);
                    }
                } catch (NoSuchFieldException | SecurityException ex) {
                    // ignore
                }
                this.combinedAnnotations = anns;
            }
            return anns;
        }

        @Override
        public String getParameterName() {
            return this.parameterName;
        }
    }
}
