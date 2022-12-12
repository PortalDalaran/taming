package io.github.portaldalaran.taming.mybatisplus;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.taming.pojo.QueryCriteria;

/**
 * simple class of QueryCriteriaWrapperBuilder
 *
 * @param <T>
 */
public class QueryBuilder<T> {
    private QueryCriteriaWrapperBuilder<T> queryCriteriaWrapperBuilder;

    public QueryBuilder() {
        this.queryCriteriaWrapperBuilder = new QueryCriteriaWrapperBuilder<>();
    }

    public QueryBuilder(QueryWrapper<T> wrapper) {
        this.queryCriteriaWrapperBuilder = new QueryCriteriaWrapperBuilder<>(wrapper);
    }

    public <V extends QueryCriteria<T>> void build(V criteriaVO) {
        queryCriteriaWrapperBuilder.build(criteriaVO);
    }

    public QueryWrapper<T> getQueryWrapper() {
        return queryCriteriaWrapperBuilder.getQueryWrapper();
    }
}
