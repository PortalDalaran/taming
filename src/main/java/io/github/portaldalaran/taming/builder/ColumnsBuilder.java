package io.github.portaldalaran.taming.builder;

import com.google.common.base.Joiner;
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
import io.github.portaldalaran.taming.utils.QueryCriteriaConstants;
import io.github.portaldalaran.taming.utils.SqlUtils;
import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * build sql select columns from criteriaVO
 * @param <T>
 */
public class ColumnsBuilder<T> {
    private BuildHelper<T> buildHelper;

    public ColumnsBuilder(BuildHelper<T> buildHelper) {
        this.buildHelper = buildHelper;
    }

    public <V extends QueryCriteria<T>> List<String> buildFields(V criteriaVO) {
        String fieldsParams = criteriaVO.getFields();
        List<SelectAssociationFields> selectAssociationFields = criteriaVO.getSelectAssociationFields();
        if (Objects.isNull(fieldsParams)) {
            return new ArrayList<>();
        }

        if (SqlUtils.checkSqlInjection(fieldsParams)) {
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
                continue;
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
            assQueryField.setParameters(Joiner.on(",").join(selectAssField.getFieldNames()));
            buildHelper.getAssociationQueryFields().add(assQueryField);
        }

        String[] inputFieldList = fieldsParams.split(QueryCriteriaConstants.FIELD_DELIMITER);
        for (String inputField : inputFieldList) {
            //ex: count(name)/sum(name)/min(name)
            if (inputField.contains("(")) {
                String calcFun = inputField.substring(0, inputField.indexOf("(")).trim();
                String calcField = inputField.substring(inputField.indexOf("(") + 1, inputField.indexOf(")")).trim();
                //change db column name
                calcField = buildHelper.getColumn(calcField);
                if (StringUtils.isBlank(calcField)) {
                    continue;
                }
                if (!queryFields.contains(calcField)) {
                    queryFields.add(calcField);
                }
                queryFields.add(MessageFormat.format("{0}({1}) as {1}_{0}", calcFun, calcField));
            } else {
                buildHelper.addColumnToQueryFields(queryFields, inputField);
            }
        }
        return queryFields.stream().distinct().collect(Collectors.toList());
    }
}
