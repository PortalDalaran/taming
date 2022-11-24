package io.github.portaldalaran.taming.core;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.pojo.SelectAssociationFields;
import io.github.portaldalaran.taming.utils.JsonUtils;
import io.github.portaldalaran.taming.utils.QueryCriteriaConstants;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.multipart.support.StandardServletPartUtils;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.WebUtils;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.util.*;

/**
 * Rewrite according to ServletRequestDataBinder
 * @author david
 */
public class QueryCriteriaDataBinder extends WebDataBinder {

    /**
     * Create a new ServletRequestDataBinder instance, with default object name.
     *
     * @param target the target object to bind onto (or {@code null}
     *               if the binder is just used to convert a plain parameter value)
     */
    public QueryCriteriaDataBinder(@Nullable Object target) {
        super(target);
    }

    /**
     * Create a new ServletRequestDataBinder instance.
     *
     * @param target     the target object to bind onto (or {@code null}
     *                   if the binder is just used to convert a plain parameter value)
     * @param objectName the name of the target object
     */
    public QueryCriteriaDataBinder(@Nullable Object target, String objectName) {
        super(target, objectName);
    }

    public static void bindParts(HttpServletRequest request, MutablePropertyValues mpvs, boolean bindEmpty)
            throws MultipartException {

        StandardServletPartUtils.getParts(request).forEach((key, values) -> {
            if (values.size() == 1) {
                Part part = values.get(0);
                if (bindEmpty || part.getSize() > 0) {
                    mpvs.add(key, part);
                }
            } else {
                mpvs.add(key, values);
            }
        });
    }

    /**
     * Bind the parameters of the given request to this binder's target,
     * also binding multipart files in case of a multipart request.
     * <p>This call can create field errors, representing basic binding
     * errors like a required field (code "required"), or type mismatch
     * between value and bean property (code "typeMismatch").
     * <p>Multipart files are bound via their parameter name, just like normal
     * HTTP parameters: i.e. "uploadedFile" to an "uploadedFile" bean property,
     * invoking a "setUploadedFile" setter method.
     * <p>The type of the target property for a multipart file can be MultipartFile,
     * byte[], or String. Servlet Part binding is also supported when the
     * request has not been parsed to MultipartRequest via MultipartResolver.
     *
     * @param request the request with parameters to bind (can be multipart)
     * @see org.springframework.web.multipart.MultipartHttpServletRequest
     * @see org.springframework.web.multipart.MultipartRequest
     * @see org.springframework.web.multipart.MultipartFile
     * @see #bind(org.springframework.beans.PropertyValues)
     */
    public void bind(ServletRequest request) {
        Enumeration<String> paramNames = request.getParameterNames();
        Map<String, Object> params = new TreeMap<>();
        List<QueryCriteriaParam> criteriaParams = new ArrayList<>();
        Map<String, List<String>> queryRelationFields = new HashMap<>();
        while (paramNames != null && paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            String[] values = request.getParameterValues(paramName);

            String operation = "";
            //是不是使用了自定义的查询条件，如果是则转化
            //Is the user-defined query criteria used? If so, convert it
            if (paramName.contains("@")) {
                String[] paramSplits = paramName.split("@");
                paramName = paramSplits[0];
                operation = paramSplits[1];
            }

            if (values == null || values.length == 0) {
                // Do nothing, no values found at all.
                addCriteriaParams(criteriaParams, paramName, operation, null);
            } else if (values.length > 1) {
                params.put(paramName, values);
                addCriteriaParams(criteriaParams, paramName, operation, values);
            } else {
                params.put(paramName, values[0]);
                addCriteriaParams(criteriaParams, paramName, operation, values[0]);
            }

            //是否有返回关联表字段
            //Whether there are returned associated table fields
            if (QueryCriteriaConstants.FIELDS_OPERATOR.equalsIgnoreCase(paramName) && paramName.contains(QueryCriteriaConstants.RELATION_DELIMITER)) {
                String[] inputFieldsByRelation = StringUtils.split(paramName, QueryCriteriaConstants.RELATION_DELIMITER);
                List<String> relationFieldNames = queryRelationFields.get(inputFieldsByRelation[0]);
                if (Objects.isNull(relationFieldNames)) {
                    relationFieldNames = Arrays.asList(inputFieldsByRelation[1]);
                } else {
                    relationFieldNames.add(inputFieldsByRelation[1]);
                }
                queryRelationFields.put(inputFieldsByRelation[0], relationFieldNames);
            }
        }
        //拼装查询条件
        // Assembly Query Criteria
        params.put("criteriaParams", criteriaParams);
        List<SelectAssociationFields> selectAssociationFields = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : queryRelationFields.entrySet()) {
            selectAssociationFields.add(new SelectAssociationFields(entry.getKey(), entry.getValue()));
        }
        //拼装关联表字段
        //Assembling association table fields
        params.put("selectAssociationFields", selectAssociationFields);
//        MutablePropertyValues mpvs = new ServletRequestParameterPropertyValues(request);
        MutablePropertyValues mpvs = new MutablePropertyValues(params);

        MultipartRequest multipartRequest = WebUtils.getNativeRequest(request, MultipartRequest.class);
        if (multipartRequest != null) {
            bindMultipart(multipartRequest.getMultiFileMap(), mpvs);
        } else if (StringUtils.startsWithIgnoreCase(request.getContentType(), MediaType.MULTIPART_FORM_DATA_VALUE)) {
            HttpServletRequest httpServletRequest = WebUtils.getNativeRequest(request, HttpServletRequest.class);
            if (httpServletRequest != null && HttpMethod.POST.matches(httpServletRequest.getMethod())) {
                bindParts(httpServletRequest, mpvs, isBindEmptyMultipartFiles());
            }
        }
        addBindValues(mpvs, request);
        doBind(mpvs);
    }

    private void addCriteriaParams(List<QueryCriteriaParam> criteriaParams, String paramName, String operation, Object value) {
        //如果是 or 和and则特殊处理
        //Special treatment for or and
        if (QueryCriteriaConstants.OR_OPERATOR.equalsIgnoreCase(paramName) || QueryCriteriaConstants.AND_OPERATOR.equalsIgnoreCase(paramName)) {
            //value是对应{xxx:xxx}
            Map<String, Object> paramMap = JsonUtils.parseObject(value.toString(), new TypeReference<Map<String, Object>>() {
            });
            List<QueryCriteriaParam> queryChildCriteriaParams = new ArrayList<>();
            for (Map.Entry<String, Object> entry : paramMap.entrySet()) {
                String key = entry.getKey();
                //判断是否有操作符
                //Determine whether there is an operator
                if (key.contains(QueryCriteriaConstants.OPTION_DELIMITER)) {
                    String[] keySplits = key.split(QueryCriteriaConstants.OPTION_DELIMITER);
                    addCriteriaParams(queryChildCriteriaParams, keySplits[0], keySplits[1], entry.getValue().toString());
                } else {
                    addCriteriaParams(queryChildCriteriaParams, key, null, entry.getValue().toString());
                }
            }
            criteriaParams.add(new QueryCriteriaParam(paramName, operation, queryChildCriteriaParams, null));
        } else if (!QueryCriteriaConstants.FIELDS_OPERATOR.equalsIgnoreCase(paramName) && !QueryCriteriaConstants.GROUP_BY_OPERATOR.equalsIgnoreCase(paramName)
                && !QueryCriteriaConstants.ORDER_BY_OPERATOR.equalsIgnoreCase(paramName) && !QueryCriteriaConstants.HAVING_OPERATOR.equalsIgnoreCase(paramName)
                && !QueryCriteriaConstants.PAGE_NO.equalsIgnoreCase(paramName) && !QueryCriteriaConstants.PAGE_SIZE.equalsIgnoreCase(paramName)) {
            //排除在QueryCriteria中的字段
            //Fields excluded from QueryCriteria
            criteriaParams.add(new QueryCriteriaParam(paramName, operation, value));
        }

    }

    protected void bindMultipart(Map<String, List<MultipartFile>> multipartFiles, MutablePropertyValues mpvs) {
        multipartFiles.forEach((key, values) -> {
            if (values.size() == 1) {
                MultipartFile value = values.get(0);
                if (isBindEmptyMultipartFiles() || !value.isEmpty()) {
                    mpvs.add(key, value);
                }
            } else {
                mpvs.add(key, values);
            }
        });
    }

    /**
     * Extension point that subclasses can use to add extra bind values for a
     * request. Invoked before {@link #doBind(MutablePropertyValues)}.
     * The default implementation is empty.
     * <p>
     * Merge URI variables into the property values to use for data binding.
     *
     * @param mpvs    the property values that will be used for data binding
     * @param request the current request
     */
    protected void addBindValues(MutablePropertyValues mpvs, ServletRequest request) {
        String attr = HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE;
        Map<String, String> uriVars = (Map) request.getAttribute(attr);
        if (uriVars != null) {
            uriVars.forEach((name, value) -> {
                if (mpvs.contains(name)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("URI variable '" + name + "' overridden by request bind value.");
                    }
                } else {
                    mpvs.addPropertyValue(name, value);
                }

            });
        }
    }

    /**
     * Treats errors as fatal.
     * <p>Use this method only if it's an error if the input isn't valid.
     * This might be appropriate if all input is from dropdowns, for example.
     *
     * @throws ServletRequestBindingException subclass of ServletException on any binding problem
     */
    public void closeNoCatch() throws ServletRequestBindingException {
        if (getBindingResult().hasErrors()) {
            throw new ServletRequestBindingException(
                    "Errors binding onto object '" + getBindingResult().getObjectName() + "'",
                    new BindException(getBindingResult()));
        }
    }

}
