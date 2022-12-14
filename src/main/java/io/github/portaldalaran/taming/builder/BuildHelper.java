package io.github.portaldalaran.taming.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.talons.meta.AssociationQueryField;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.BuildUtils;
import io.github.portaldalaran.taming.utils.QueryCriteriaConstants;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * build tools
 *
 * @param <T>
 */
public class BuildHelper<T> {
    @Getter
    private List<AssociationQueryField> associationQueryFields = new ArrayList<>();
    @Getter
    private Class<T> modelClass;
    @Getter
    private List<String> buildEntityFieldNames;
    @Setter
    @Getter
    private List<QueryCriteriaParam<T>> queryCriteriaParams;

    public BuildHelper(Class<T> modelClass) {
        this.modelClass = modelClass;

    }

    public <V extends QueryCriteria<T>> void init(V criteriaVO) {
        buildEntityFieldNames = BuildUtils.getAllDeclaredFields(criteriaVO.getClass()).stream().map(Field::getName).collect(Collectors.toList());
    }

    public <V extends QueryCriteria<T>> CriteriaParamsBinder<V, T> newCriteriaParamsBinder(V criteriaVO) {
        return new CriteriaParamsBinder(criteriaVO, this);
    }

    public ColumnsBuilder<T> newColumnsBuilder() {
        return new ColumnsBuilder<T>(this);
    }

    public <V extends QueryCriteria<T>> GroupParamsBuilder<V, T> newGroupParamsBuilder(QueryWrapper<T> queryWrapper, V criteriaVO) {
        return new GroupParamsBuilder<>(queryWrapper, criteriaVO, this);
    }

    public WhereParamsBuilder<T> newWhereParamsBuilder() {
        return new WhereParamsBuilder<>(this);
    }

    public OrderBuilder<T> newOrderBuilder(QueryWrapper<T> queryWrapper) {
        return new OrderBuilder<>(queryWrapper, this);
    }

    public String getColumn(String field) {
        return BuildUtils.getColumn(field, this.modelClass);
    }

    public void addColumnToQueryFields(List<String> queryFields, String fieldName) {
        String column = getColumn(fieldName);
        if (StringUtils.isNotBlank(column) && !queryFields.contains(column)) {
            queryFields.add(column);
        }
    }

    /**
     * paramName?????????build VO?????????,?????????or,and?????????
     * ParamName must be an attribute of build VO???or is key {or,and}
     *
     * @param attributeName
     * @return
     */
    public boolean checkEntityAttribute(String attributeName) {
        if (QueryCriteriaConstants.OR_OPERATOR.equalsIgnoreCase(attributeName) || QueryCriteriaConstants.AND_OPERATOR.equalsIgnoreCase(attributeName)) {
            return true;
        }
        return buildEntityFieldNames.contains(attributeName);
    }
}
