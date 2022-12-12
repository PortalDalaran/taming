package io.github.portaldalaran.taming.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.utils.QueryCriteriaConstants;
import io.github.portaldalaran.taming.utils.SqlUtils;
import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;

/**
 * build group by and having parameters
 * @param <V>
 * @param <T>
 */
public class GroupParamsBuilder<V extends QueryCriteria<T>, T> {
    private BuildHelper<T> buildHelper;
    private QueryWrapper<T> queryWrapper;
    private V criteriaVO;

    public GroupParamsBuilder(QueryWrapper<T> queryWrapper, V criteriaVO, BuildHelper<T> buildHelper) {
        this.queryWrapper = queryWrapper;
        this.buildHelper = buildHelper;
        this.criteriaVO = criteriaVO;
    }

    public QueryWrapper<T> buildGroupBy(List<String> queryFields) {
        String groupByParams = criteriaVO.getGroupBy();
        if (Objects.isNull(groupByParams)) {
            return queryWrapper;
        }
        if (SqlUtils.checkSqlInjection(groupByParams)) {
            throw new QueryCriteriaException("Query parameter SQL injection verification failed");
        }
        //"分组  ex:\"{groupBy:\"id,name\"}\" --->SQL: group by id,name"
        List<String> groupByColumns = Lists.newArrayList();
        //如果用了groupBy在fields里自动加上 groupBy字段，groupBy字段Count
        //If groupBy is used, the groupBy field is automatically added to fields, and the groupBy field is Count
        String[] groupBySplitParams = groupByParams.split(QueryCriteriaConstants.FIELD_DELIMITER);
        for (String groupByParam : groupBySplitParams) {
            groupByParam = buildHelper.getColumn(groupByParam);

            //paramName必须是build VO的属性
            //ParamName must be an attribute of build VO
            if (StringUtils.isBlank(groupByParam) || !buildHelper.getBuildEntityFieldNames().contains(groupByParam)) {
                continue;
            }

            if (!queryFields.contains(groupByParam)) {
                queryFields.add(groupByParam);
            }
            //count(field) as field_count
            String tempFun = MessageFormat.format("count({0}) as {0}_count", groupByParam);
            if (!queryFields.contains(tempFun)) {
                queryFields.add(tempFun);
            }
            groupByColumns.add(groupByParam);
        }
        //转换为数据库字段后，重新拼装
        //After converting to database fields, reassemble
        return queryWrapper.groupBy(Joiner.on(",").join(groupByColumns));
    }


    public QueryWrapper<T> buildHaving() {
        String havingParams = criteriaVO.getHaving();
        if (Objects.isNull(havingParams)) {
            return queryWrapper;
        }
        if (SqlUtils.checkSqlInjection(havingParams)) {
            throw new QueryCriteriaException("Query parameter SQL injection verification failed");
        }
        //"分组条件(SQL)  ex:\"{having:\"sum(age)>18\"}\" --->SQL: having sum(age)>18"
        List<String> havingColumns = Lists.newArrayList();

        String[] havingList = havingParams.split(QueryCriteriaConstants.FIELD_DELIMITER);
        for (String havingStr : havingList) {
            //有括号说明是统计
            //Parentheses indicate statistics
            if (havingStr.indexOf("(") > 1) {
                //如果是统计就要把中间的字段拿 来转换
                //For statistics, the fields in the middle should be converted
                String changStr = havingStr.substring(havingStr.indexOf("(") + 1, havingStr.indexOf(")")).trim();
                String columnField = buildHelper.getColumn(changStr);
                //paramName必须是build VO的属性
                //ParamName must be an attribute of build VO
                if (StringUtils.isBlank(columnField) || !buildHelper.getBuildEntityFieldNames().contains(changStr)) {
                    continue;
                }
                havingStr = havingStr.replaceAll(changStr, columnField);
            } else {
                String changStr = "";
                //没有括号，则根据判断符号（>\<\>=\in\like）来判断
                //If there is no bracket, it is judged according to the judgment symbol (><>= in like)
                if (havingStr.indexOf(">") > 1) {
                    changStr = havingStr.substring(0, havingStr.indexOf(">")).trim();
                } else if (havingStr.indexOf("<") > 1) {
                    changStr = havingStr.substring(0, havingStr.indexOf("<")).trim();
                } else if (havingStr.indexOf("!") > 1) {
                    changStr = havingStr.substring(0, havingStr.indexOf("!")).trim();
                } else if (havingStr.indexOf("=") > 1) {
                    changStr = havingStr.substring(0, havingStr.indexOf("=")).trim();
                } else if (havingStr.toLowerCase().indexOf("in") > 1) {
                    changStr = havingStr.substring(0, havingStr.toLowerCase().indexOf("in")).trim();
                } else if (havingStr.toLowerCase().indexOf("like") > 1) {
                    changStr = havingStr.substring(0, havingStr.toLowerCase().indexOf("like")).trim();
                }
                String columnField = buildHelper.getColumn(changStr);
                //paramName必须是build VO的属性
                //ParamName must be an attribute of build VO
                if (StringUtils.isBlank(columnField) || !buildHelper.getBuildEntityFieldNames().contains(changStr)) {
                    continue;
                }

                havingStr = havingStr.replaceAll(changStr, columnField);
            }
            havingColumns.add(havingStr);
        }
        queryWrapper.having(Joiner.on(",").join(havingColumns));
        return queryWrapper;
    }
}
