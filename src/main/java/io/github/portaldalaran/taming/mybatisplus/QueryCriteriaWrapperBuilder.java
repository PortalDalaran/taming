package io.github.portaldalaran.taming.mybatisplus;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.talons.meta.AssociationQueryField;
import io.github.portaldalaran.taming.builder.BuildHelper;
import io.github.portaldalaran.taming.builder.GroupParamsBuilder;
import io.github.portaldalaran.taming.builder.WhereParamsBuilder;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.utils.ClassUtils;
import io.github.portaldalaran.taming.utils.QueryCriteriaConstants;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

/**
 * @author aohee@163.com
 */
@Slf4j
public class QueryCriteriaWrapperBuilder<T> {
    private QueryWrapper<T> queryWrapper;
    private Class<T> modelClass;
    private BuildHelper<T> buildHelper;

    public QueryCriteriaWrapperBuilder() {
        this.queryWrapper = new QueryWrapper<T>();
        this.modelClass = ClassUtils.getEntity(getClass());
        if (Objects.isNull(this.modelClass)) {
            throw new QueryCriteriaException("Annotation ParameterizedType is null");
        }
        queryWrapper.setEntityClass(modelClass);
        buildHelper = new BuildHelper<>(modelClass);
    }

    public QueryCriteriaWrapperBuilder(QueryWrapper<T> wrapper) {
        this.queryWrapper = wrapper;
        this.modelClass = ClassUtils.getEntity(getClass());
        if (Objects.isNull(this.modelClass)) {
            throw new QueryCriteriaException("Annotation ParameterizedType is null");
        }
        queryWrapper.setEntityClass(modelClass);

    }

    public QueryWrapper<T> getQueryWrapper() {
        return queryWrapper;
    }

    /**
     * 返回关联字段
     * association entity query select parameter
     * user.name,user.sex => select name,sex from user
     *
     * @return
     */
    public List<AssociationQueryField> getAssociationQueryFields() {
        return buildHelper.getAssociationQueryFields();
    }


    public <V extends QueryCriteria<T>> boolean build(V criteriaVO) {
        buildHelper = new BuildHelper<>(modelClass);
        buildHelper.init(criteriaVO);

        buildHelper.newCriteriaParamsBinder(criteriaVO).assembleCriteriaParamsByEntityValue();

        List<String> queryFields = buildHelper.newColumnsBuilder().buildFields(criteriaVO);

        GroupParamsBuilder<V, T> groupParamsBuilder = buildHelper.newGroupParamsBuilder(queryWrapper, criteriaVO);
        groupParamsBuilder.buildGroupBy(queryFields);
        groupParamsBuilder.buildHaving();

        buildHelper.newOrderBuilder(queryWrapper).buildOrderBy(criteriaVO.getOrderBy());

        WhereParamsBuilder<T> whereParamsBuilder = buildHelper.newWhereParamsBuilder();
        //处理where条件
        buildHelper.getQueryCriteriaParams().forEach(queryCriteriaParam -> whereParamsBuilder.buildCriteriaParam(queryWrapper, queryCriteriaParam));

        if (!queryFields.isEmpty()) {
            queryWrapper.select(String.join(QueryCriteriaConstants.FIELD_DELIMITER, queryFields));
        }
        return true;
    }
}