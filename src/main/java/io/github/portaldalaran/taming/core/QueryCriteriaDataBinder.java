package io.github.portaldalaran.taming.core;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.pojo.SelectAssociationFields;
import io.github.portaldalaran.taming.utils.JsonUtils;
import io.github.portaldalaran.taming.utils.QueryConstants;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrite according to ServletRequestDataBinder
 *
 * @author david
 */
public class QueryCriteriaDataBinder extends WebDataBinder {

    private static final Pattern BRACKET_PATTERN = Pattern.compile("(?<=\\[).+?(?=\\])");

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
     *
     * @param request the request with parameters to bind (can be multipart)
     * @see #bind(org.springframework.beans.PropertyValues)
     */
    public void bind(ServletRequest request) {
        Enumeration<String> paramNames = request.getParameterNames();
        Map<String, Object> params = new HashMap<>();
        List<QueryCriteriaParam<?>> criteriaParams = new ArrayList<>();
        Map<String, List<String>> queryRelationFields = new HashMap<>();
        while (paramNames != null && paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            String[] values = request.getParameterValues(paramName);

            //比如传入or:{xx:xx,yy:yy}的情况，会有值or[xx]:xx,or[yy]:yy格式, users[0]=user1, users[1]=user2
            EntityParamNames entityParamNames = buildParamNames(paramName);

            String operation = "";
            //是不是使用了自定义的查询条件，如果是则转化（只拆第一个 @）
            String rawParamName = entityParamNames.getParamName();
            int atIdx = rawParamName.indexOf(QueryConstants.OPTION_DELIMITER);
            if (atIdx >= 0) {
                entityParamNames.setParamName(rawParamName.substring(0, atIdx));
                operation = rawParamName.substring(atIdx + 1);
            }

            if (values == null || values.length == 0) {
                addCriteriaParams(criteriaParams, entityParamNames, operation, null);
            } else if (values.length > 1) {
                putBindProperty(params, entityParamNames, operation, values);
                addCriteriaParams(criteriaParams, entityParamNames, operation, values);
            } else {
                putBindProperty(params, entityParamNames, operation, values[0]);
                addCriteriaParams(criteriaParams, entityParamNames, operation, values[0]);
            }

            // 关联投影：user.name → 选关联表字段；不作为 WHERE（点号字段不是 VO 属性）
            if (entityParamNames.getParamName().contains(QueryConstants.RELATION_DELIMITER)) {
                String[] inputFieldsByRelation = StringUtils.split(
                        entityParamNames.getParamName(), QueryConstants.RELATION_DELIMITER);
                if (inputFieldsByRelation != null && inputFieldsByRelation.length >= 2) {
                    List<String> relationFieldNames =
                            queryRelationFields.computeIfAbsent(inputFieldsByRelation[0], k -> new ArrayList<>());
                    relationFieldNames.add(inputFieldsByRelation[1]);
                }
            }
        }
        // fields=id,user.name,user.sex → 拆出关联投影，主表字段保留在 fields
        Object fieldsValue = params.get(QueryConstants.FIELDS);
        if (fieldsValue instanceof String) {
            String rewritten = extractRelationFieldsFromFieldsParam((String) fieldsValue, queryRelationFields);
            if (rewritten != null) {
                params.put(QueryConstants.FIELDS, rewritten);
            }
        }
        //拼装查询条件
        params.put("criteriaParams", criteriaParams);
        List<SelectAssociationFields> selectAssociationFields = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : queryRelationFields.entrySet()) {
            selectAssociationFields.add(new SelectAssociationFields(entry.getKey(), entry.getValue()));
        }
        //拼装关联表字段
        params.put("selectAssociationFields", selectAssociationFields);
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

    /**
     * 比如传入or:{xx:xx,yy:yy}的情况，会有值or[xx]:xx,or[yy]:yy格式，则把name处理了
     */
    private EntityParamNames buildParamNames(String paramName) {
        String prefixParamName = "";
        Matcher matcher = BRACKET_PATTERN.matcher(paramName);
        if (matcher.find()) {
            prefixParamName = paramName.split("\\[")[0];
            paramName = matcher.group(0);
        }
        return new EntityParamNames(prefixParamName, paramName);
    }

    @SuppressWarnings("unchecked")
    private void addCriteriaParams(List<QueryCriteriaParam<?>> criteriaParams, EntityParamNames entityParamNames, String operation, Object value) {
        // 如果是比如传入or:{xx:xx,yy:yy}的情况，会有值or[xx]:xx,or[yy]:yy格式
        if (entityParamNames.isOperatorByPrefixParamName()) {
            QueryCriteriaParam<?> temp = entityParamNames.findQueryCriteriaByPrefixParamName(criteriaParams);
            if (Objects.isNull(temp)) {
                List<QueryCriteriaParam<?>> queryChildCriteriaParams = new ArrayList<>();
                buildParamValue(entityParamNames.getParamName(), operation, value, queryChildCriteriaParams);
                criteriaParams.add(new QueryCriteriaParam<>(entityParamNames.getPrefixParamName(), null, queryChildCriteriaParams));
            } else {
                List<QueryCriteriaParam<?>> queryChildCriteriaParams = (List<QueryCriteriaParam<?>>) temp.getValue();
                buildParamValue(entityParamNames.getParamName(), operation, value, queryChildCriteriaParams);
                temp.setValue(queryChildCriteriaParams);
            }
        } else if (entityParamNames.isApplySqlOperator()) {
            //applySql[0]="date_format(dateColumn,'%Y-%m-%d') = {0}" , applySql[1]="2008-08-08"
            QueryCriteriaParam<?> temp = entityParamNames.findQueryCriteriaByPrefixParamName(criteriaParams);
            if (Objects.isNull(temp)) {
                List<QueryCriteriaParam<?>> queryChildCriteriaParams = new ArrayList<>();
                queryChildCriteriaParams.add(new QueryCriteriaParam<>(entityParamNames.getParamName(), operation, value));
                criteriaParams.add(new QueryCriteriaParam<>(entityParamNames.getPrefixParamName(), QueryConstants.APPLY_SQL, queryChildCriteriaParams));
            } else {
                List<QueryCriteriaParam<?>> queryChildCriteriaParams = (List<QueryCriteriaParam<?>>) temp.getValue();
                queryChildCriteriaParams.add(new QueryCriteriaParam<>(entityParamNames.getParamName(), operation, value));
                temp.setValue(queryChildCriteriaParams);
            }
        } else if (entityParamNames.isOperatorByParamName()) {
            //如果是 or 和and则特殊处理
            QueryCriteriaParam<?> temp = entityParamNames.findQueryCriteriaByParamName(criteriaParams);
            if (Objects.isNull(temp)) {
                List<QueryCriteriaParam<?>> queryChildCriteriaParams = new ArrayList<>();
                buildParamValue(entityParamNames.getParamName(), operation, value, queryChildCriteriaParams);
                criteriaParams.add(new QueryCriteriaParam<>(entityParamNames.getParamName(), null, queryChildCriteriaParams));
            } else {
                List<QueryCriteriaParam<?>> queryChildCriteriaParams = (List<QueryCriteriaParam<?>>) temp.getValue();
                buildParamValue(entityParamNames.getParamName(), operation, value, queryChildCriteriaParams);
                temp.setValue(queryChildCriteriaParams);
            }
        } else if (entityParamNames.isUserDefinedField()) {
            // 排除 users[0] 下标；关联投影 user.name 不进 WHERE
            if (!entityParamNames.isNumber()
                    && !entityParamNames.getParamName().contains(QueryConstants.RELATION_DELIMITER)) {
                criteriaParams.add(new QueryCriteriaParam<>(entityParamNames.getParamName(), operation, value));
            }
        }
    }

    /**
     * 写入 Spring 属性绑定用的 key：带 @ 算子或关联投影不进 bean 属性，避免污染 {@code name@like} 之类键。
     */
    private void putBindProperty(Map<String, Object> params, EntityParamNames entityParamNames,
                                 String operation, Object value) {
        if (StringUtils.hasText(operation)) {
            return;
        }
        String bindName = entityParamNames.getParamName();
        if (!StringUtils.hasText(bindName)
                || bindName.contains(QueryConstants.RELATION_DELIMITER)) {
            return;
        }
        params.put(bindName, value);
    }

    /**
     * 从 fields 参数中拆出 {@code entity.field} 关联投影，返回仅含主表字段的 fields 字符串。
     */
    private String extractRelationFieldsFromFieldsParam(String fieldsParams,
                                                        Map<String, List<String>> queryRelationFields) {
        if (!StringUtils.hasText(fieldsParams)
                || !fieldsParams.contains(QueryConstants.RELATION_DELIMITER)) {
            return null;
        }
        List<String> mainFields = new ArrayList<>();
        for (String part : fieldsParams.split(QueryConstants.FIELD_DELIMITER)) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            String field = part.trim();
            if (field.contains(QueryConstants.RELATION_DELIMITER)) {
                String[] parts = StringUtils.split(field, QueryConstants.RELATION_DELIMITER);
                if (parts != null && parts.length >= 2) {
                    queryRelationFields.computeIfAbsent(parts[0], k -> new ArrayList<>()).add(parts[1]);
                }
            } else {
                mainFields.add(field);
            }
        }
        return String.join(QueryConstants.FIELD_DELIMITER, mainFields);
    }

    private void buildParamValue(String paramName, String operation, Object value, List<QueryCriteriaParam<?>> queryChildCriteriaParams) {
        if (value != null && value.toString().contains("{")) {
            Map<String, Object> paramMap = tryParseJsonMap(value.toString());
            if (paramMap == null) {
                // 含 { 但不是合法 JSON（如普通文本），按字面值条件处理
                queryChildCriteriaParams.add(new QueryCriteriaParam<>(paramName, operation, value));
                return;
            }
            for (Map.Entry<String, Object> entry : paramMap.entrySet()) {
                String key = entry.getKey();
                EntityParamNames prefixKey = buildParamNames(key);
                //判断是否有操作符（只拆第一个 @）
                int keyAt = key.indexOf(QueryConstants.OPTION_DELIMITER);
                if (keyAt >= 0) {
                    prefixKey.setParamName(key.substring(0, keyAt));
                    addCriteriaParams(queryChildCriteriaParams, prefixKey, key.substring(keyAt + 1), entry.getValue());
                } else {
                    addCriteriaParams(queryChildCriteriaParams, prefixKey, null, entry.getValue());
                }
            }
        } else {
            queryChildCriteriaParams.add(new QueryCriteriaParam<>(paramName, operation, value));
        }
    }

    private Map<String, Object> tryParseJsonMap(String text) {
        try {
            return JsonUtils.parseObject(text, new TypeReference<Map<String, Object>>() {
            });
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Override
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
     * Merge URI variables into the property values to use for data binding.
     *
     * @param mpvs    the property values that will be used for data binding
     * @param request the current request
     */
    @SuppressWarnings("unchecked")
    protected void addBindValues(MutablePropertyValues mpvs, ServletRequest request) {
        String attr = HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE;
        Map<String, String> uriVars = (Map<String, String>) request.getAttribute(attr);
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
