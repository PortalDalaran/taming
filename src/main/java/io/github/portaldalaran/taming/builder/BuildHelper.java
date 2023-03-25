package io.github.portaldalaran.taming.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.talons.meta.AssociationQueryField;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.pojo.QueryCriteriaParam;
import io.github.portaldalaran.taming.utils.BuildUtils;
import io.github.portaldalaran.taming.utils.QueryConstants;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * build tools
 *
 * @param <T>
 */
public class BuildHelper<T> {
    @Getter
    protected List<AssociationQueryField> associationQueryFields = new ArrayList<>();
    @Getter
    private Class<T> modelClass;
    @Getter
    private List<String> buildEntityFieldNames;
    /**
     * 包括传入的joinclass的列表
     */
    @Getter
    private List<Field> buildEntityFields;
    @Getter
    private List<Class<?>> joinClassList;
    @Setter
    @Getter
    protected List<QueryCriteriaParam<T>> queryCriteriaParams;

    /**
     * inJoinClassList 使用场景，mapper.xml中inner join后不在默认对象中，在关联对象里的查询字段
     * Parameter<inJoinClassList>usage scenario. After inner join in mapper.xml, it is not in the default object, but in the query field of the associated object
     *
     * @param modelClass      默认对象class
     * @param inJoinClassList 追加对象class列表
     */
    public BuildHelper(Class<T> modelClass, List<Class<?>> inJoinClassList) {
        this.modelClass = modelClass;
        this.joinClassList = inJoinClassList;
    }

    public <V extends QueryCriteria<T>> void init(V criteriaVO) {
        buildEntityFields = BuildUtils.getAllDeclaredFields(criteriaVO.getClass());
        buildEntityFieldNames = buildEntityFields.stream().map(Field::getName).collect(Collectors.toList());

        //add joinClassList fieldNames
        if (Objects.nonNull(joinClassList)) {
            for (Class<?> joinClass : joinClassList) {
                List<Field> tempFieldList = BuildUtils.getAllDeclaredFields(joinClass);
                List<String> tempFieldNameList = tempFieldList.stream().map(Field::getName).collect(Collectors.toList());
                for (int i = 0; i < tempFieldNameList.size(); i++) {
                    String tempFieldName = tempFieldNameList.get(i);
                    if (!buildEntityFieldNames.contains(tempFieldName)) {
                        buildEntityFieldNames.add(tempFieldName);
                        buildEntityFields.add(tempFieldList.get(i));
                    }
                }

            }
        }
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
        String column = BuildUtils.getColumn(field, this.modelClass);
        //若在model里找不到，则到joinClass中去找
        if (StringUtils.isBlank(column)) {
            for (Class<?> joinClass : joinClassList) {
                column = BuildUtils.getColumn(field, joinClass);
                if (StringUtils.isNotBlank(column)) {
                    break;
                }
            }
        }
        return column;
    }

    public void addColumnToQueryFields(List<String> queryFields, String fieldName) {
        String column = getColumn(fieldName);
        if (StringUtils.isNotBlank(column) && !queryFields.contains(column)) {
            queryFields.add(column);
        }
    }

    /**
     * paramName必须是build VO的属性,或者是or,and关键字
     * ParamName must be an attribute of build VO，or is key {or,and}
     *
     * @param attributeName
     * @return
     */
    public boolean checkEntityAttribute(String attributeName) {
        if (QueryConstants.OR.equalsIgnoreCase(attributeName) || QueryConstants.AND.equalsIgnoreCase(attributeName) || QueryConstants.NESTED.equalsIgnoreCase(attributeName)) {
            return true;
        }
        return buildEntityFieldNames.contains(attributeName);
    }
}
