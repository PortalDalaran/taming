package io.github.portaldalaran.taming.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.utils.QueryConstants;
import io.github.portaldalaran.taming.utils.SqlUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * builder select order parameters
 * @author david
 * @param <T>
 */
public class OrderBuilder<T> {
    protected BuildHelper<T> buildHelper;
    protected QueryWrapper<T> queryWrapper;

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

        String[] orders = orderBy.split(QueryConstants.FIELD_DELIMITER);
        for (String order : orders) {
            if (StringUtils.isBlank(order)) {
                continue;
            }
            String[] tempOrder = order.trim().split(QueryConstants.OPTION_DELIMITER);
            String fieldName = tempOrder[0].trim();
            String direction = tempOrder.length > 1 ? tempOrder[1].trim() : QueryConstants.ASC;
            if (!direction.equalsIgnoreCase(QueryConstants.DESC)
                    && !direction.equalsIgnoreCase(QueryConstants.ASC)) {
                throw new QueryCriteriaException("Invalid orderBy direction: " + direction);
            }
            //paramName必须是build VO的属性
            //ParamName must be an attribute of build VO
            if (!buildHelper.checkEntityAttribute(fieldName)) {
                throw new QueryCriteriaException("Invalid orderBy attribute: " + fieldName);
            }
            String orderColumn = buildHelper.getColumn(fieldName);
            if (StringUtils.isBlank(orderColumn)) {
                throw new QueryCriteriaException("Cannot resolve column for orderBy: " + fieldName);
            }

            if (direction.equalsIgnoreCase(QueryConstants.DESC)) {
                queryWrapper.orderByDesc(orderColumn);
            } else {
                queryWrapper.orderByAsc(orderColumn);
            }
        }
        return queryWrapper;
    }

}
