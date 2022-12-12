package io.github.portaldalaran.taming.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.utils.QueryCriteriaConstants;
import io.github.portaldalaran.taming.utils.SqlUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * builder select order parameters
 * @param <T>
 */
public class OrderBuilder<T> {
    private BuildHelper<T> buildHelper;
    private QueryWrapper<T> queryWrapper;

    public OrderBuilder(QueryWrapper<T> queryWrapper, BuildHelper<T> buildHelper) {
        this.buildHelper = buildHelper;
        this.queryWrapper = queryWrapper;
    }

    public QueryWrapper<T> buildOrderBy(String orderBy) {
        if (Objects.isNull(orderBy)) {
            return queryWrapper;
        }
        if (SqlUtils.checkSqlInjection(orderBy)) {
            throw new QueryCriteriaException("Query parameter SQL injection verification failed");
        }

        String[] orders = orderBy.split(QueryCriteriaConstants.FIELD_DELIMITER);
        for (String order : orders) {
            String[] tempOrder = order.split(QueryCriteriaConstants.OPTION_DELIMITER);
            String orderColumn = buildHelper.getColumn(tempOrder[0]);
            //paramName必须是build VO的属性
            //ParamName must be an attribute of build VO
            if (StringUtils.isBlank(orderColumn) || !buildHelper.getBuildEntityFieldNames().contains(tempOrder[0])) {
                continue;
            }

            if (tempOrder[1].equalsIgnoreCase(QueryCriteriaConstants.DESC_OPERATOR)) {
                queryWrapper.orderByDesc(orderColumn);
            } else {
                queryWrapper.orderByAsc(orderColumn);
            }
        }
        return queryWrapper;
    }

}
