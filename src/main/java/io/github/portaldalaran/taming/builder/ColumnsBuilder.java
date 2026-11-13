package io.github.portaldalaran.taming.builder;

import io.github.portaldalaran.talons.annotation.JoinColumn;
import io.github.portaldalaran.talons.core.TalonsHelper;
import io.github.portaldalaran.talons.exception.TalonsException;
import io.github.portaldalaran.talons.meta.AssociationFieldInfo;
import io.github.portaldalaran.talons.meta.AssociationQueryField;
import io.github.portaldalaran.talons.meta.AssociationTableInfo;
import io.github.portaldalaran.talons.meta.AssociationType;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.pojo.SelectAssociationFields;
import io.github.portaldalaran.taming.utils.QueryConstants;
import io.github.portaldalaran.taming.utils.SqlUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * build sql select columns from criteriaVO
 * @param <T>
 */
public class ColumnsBuilder<T> {
    protected BuildHelper<T> buildHelper;

    public ColumnsBuilder(BuildHelper<T> buildHelper) {
        this.buildHelper = buildHelper;
    }

    public <V extends QueryCriteria<T>> List<String> buildFields(V criteriaVO) {
        String fieldsParams = criteriaVO.getFields();
        List<SelectAssociationFields> selectAssociationFields = criteriaVO.getSelectAssociationFields();
        if (Objects.isNull(fieldsParams)) {
            return new ArrayList<>();
        }
        if (selectAssociationFields == null) {
            selectAssociationFields = List.of();
        }

        // fields 可含函数与引号字面量，用片段级校验
        if (SqlUtils.checkSqlFragment(fieldsParams)) {
            throw new QueryCriteriaException("Query parameter SQL injection verification failed");
        }

        //Association table info from mybatisplus definition
        AssociationTableInfo<T> rsTableInfo = TalonsHelper.init(buildHelper.getModelClass());
        List<AssociationFieldInfo> rsTableInfoAnnotations = rsTableInfo.getAnnotations();

        List<String> queryFields = new ArrayList<>();
        for (SelectAssociationFields selectAssField : selectAssociationFields) {
            if (SqlUtils.checkSqlInjection(selectAssField.getEntityName())) {
                throw new TalonsException("Query parameter SQL injection verification failed");
            }
            //通过Annotations找到与关联表对应的字段名称
            //Find the field name corresponding to the associated table through Annotations
            AssociationFieldInfo rsFieldInfo = rsTableInfoAnnotations.stream()
                    .filter(rsf -> rsf.getName().equals(selectAssField.getEntityName()))
                    .findFirst().orElse(null);

            if (Objects.isNull(rsFieldInfo)) {
                throw new QueryCriteriaException(
                        "Unknown association entity in fields: " + selectAssField.getEntityName());
            }
            if (rsFieldInfo.getAssociationType() == AssociationType.MANYTOONE) {
                String m2oId = selectAssField.getEntityName() + "Id";
                //改为不判断，直接加上，在后边有去掉重复的字段
                //Add directly, and then there are fields to remove duplicates
                buildHelper.addColumnToQueryFields(queryFields, m2oId);
            }
            //如果关联对象有多个关系字段，则要加入到查询的fields中
            //If the associated object has multiple relation fields, it should be added to the fields of the query
            List<JoinColumn> joinColumns = rsFieldInfo.getJoinColumns();
            joinColumns.forEach(jc -> buildHelper.addColumnToQueryFields(queryFields, jc.name()));

            AssociationQueryField assQueryField = new AssociationQueryField();
            assQueryField.setTableName(selectAssField.getEntityName());
            assQueryField.setParameters(String.join(QueryConstants.FIELD_DELIMITER, selectAssField.getFieldNames()));
            buildHelper.getAssociationQueryFields().add(assQueryField);
        }

        for (String inputField : GroupParamsBuilder.splitHavingClauses(fieldsParams)) {
            if (StringUtils.isBlank(inputField)) {
                continue;
            }
            inputField = inputField.trim();
            //ex: count(name)/sum(name)/min(name)/date_format(created,'%Y')
            int leftParen = inputField.indexOf('(');
            if (leftParen > 0) {
                int rightParen = GroupParamsBuilder.findMatchingParen(inputField, leftParen);
                if (rightParen < 0) {
                    throw new QueryCriteriaException("Invalid fields expression: " + inputField);
                }
                String calcFun = inputField.substring(0, leftParen).trim();
                String inside = inputField.substring(leftParen + 1, rightParen);
                String fieldToken = GroupParamsBuilder.extractFieldToken(inside);
                String calcField = buildHelper.getColumn(fieldToken);
                if (StringUtils.isBlank(calcField)) {
                    continue;
                }
                if (!queryFields.contains(calcField)) {
                    queryFields.add(calcField);
                }
                String newInside = replaceFirstToken(inside, fieldToken, calcField);
                queryFields.add(calcFun + "(" + newInside + ") as " + calcField + "_" + calcFun);
            } else {
                buildHelper.addColumnToQueryFields(queryFields, inputField);
            }
        }
        return queryFields.stream().distinct().collect(Collectors.toList());
    }

    private static String replaceFirstToken(String source, String token, String replacement) {
        if (StringUtils.isBlank(token) || source == null) {
            return source;
        }
        int idx = source.indexOf(token);
        if (idx < 0) {
            return source;
        }
        return source.substring(0, idx) + replacement + source.substring(idx + token.length());
    }
}
