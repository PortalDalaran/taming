package io.github.portaldalaran.taming.builder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.portaldalaran.taming.core.QueryCriteriaException;
import io.github.portaldalaran.taming.pojo.QueryCriteria;
import io.github.portaldalaran.taming.utils.QueryConstants;
import io.github.portaldalaran.taming.utils.SqlUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * build group by and having parameters
 * @author aohee@163.com
 * @param <V>
 * @param <T>
 */
public class GroupParamsBuilder<V extends QueryCriteria<T>, T> {
    protected BuildHelper<T> buildHelper;
    protected QueryWrapper<T> queryWrapper;
    protected V criteriaVO;

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
        List<String> groupByColumns = new ArrayList<>();
        //如果用了groupBy在fields里自动加上 groupBy字段，groupBy字段Count
        //If groupBy is used, the groupBy field is automatically added to fields, and the groupBy field is Count
        String[] groupBySplitParams = groupByParams.split(QueryConstants.FIELD_DELIMITER);
        for (String groupByParam : groupBySplitParams) {
            if (StringUtils.isBlank(groupByParam)) {
                continue;
            }
            String fieldName = groupByParam.trim();
            //paramName必须是build VO的属性
            //ParamName must be an attribute of build VO
            if (!buildHelper.checkEntityAttribute(fieldName)) {
                throw new QueryCriteriaException("Invalid groupBy attribute: " + fieldName);
            }

            String column = buildHelper.getColumn(fieldName);
            if (StringUtils.isBlank(column)) {
                throw new QueryCriteriaException("Cannot resolve column for groupBy: " + fieldName);
            }

            if (!queryFields.contains(column)) {
                queryFields.add(column);
            }
            //count(field) as field_count — 不用 MessageFormat，避免列名中的单引号被吃掉
            String tempFun = "count(" + column + ") as " + column + "_count";
            if (!queryFields.contains(tempFun)) {
                queryFields.add(tempFun);
            }
            groupByColumns.add(column);
        }
        //转换为数据库字段后，重新拼装
        //After converting to database fields, reassemble
        if (!groupByColumns.isEmpty()) {
            queryWrapper.groupBy(String.join(QueryConstants.FIELD_DELIMITER, groupByColumns));
        }
        return queryWrapper;
    }


    public QueryWrapper<T> buildHaving() {
        String havingParams = criteriaVO.getHaving();
        if (Objects.isNull(havingParams)) {
            return queryWrapper;
        }
        // having 允许函数与引号字面量，用片段级校验（注释 / 堆叠语句）
        if (SqlUtils.checkSqlFragment(havingParams)) {
            throw new QueryCriteriaException("Query parameter SQL injection verification failed");
        }
        //"分组条件(SQL)  ex:\"{having:\"sum(age)>18\"}\" --->SQL: having sum(age)>18"
        List<String> havingColumns = new ArrayList<>();

        for (String havingStr : splitHavingClauses(havingParams)) {
            if (StringUtils.isBlank(havingStr)) {
                continue;
            }
            havingStr = havingStr.trim();
            int leftParen = havingStr.indexOf('(');
            int rightParen = leftParen >= 0 ? findMatchingParen(havingStr, leftParen) : -1;
            if (leftParen >= 0 && rightParen > leftParen) {
                // 统计函数：只转换括号内第一个字段标识符（兼容 date_format(created,'%Y')、sum(ifnull(age,0))）
                String inside = havingStr.substring(leftParen + 1, rightParen);
                String fieldToken = extractFieldToken(inside);
                String columnField = buildHelper.getColumn(fieldToken);
                if (StringUtils.isBlank(columnField) || !buildHelper.checkEntityAttribute(fieldToken)) {
                    throw new QueryCriteriaException("Invalid having attribute: " + fieldToken);
                }
                String newInside = replaceFirstToken(inside, fieldToken, columnField);
                havingStr = havingStr.substring(0, leftParen + 1) + newInside + havingStr.substring(rightParen);
            } else {
                String changStr = "";
                //没有括号，则根据判断符号（>\<\>=\in\like）来判断
                String lowerHaving = havingStr.toLowerCase();
                if (havingStr.indexOf('>') > 0) {
                    changStr = havingStr.substring(0, havingStr.indexOf('>')).trim();
                } else if (havingStr.indexOf('<') > 0) {
                    changStr = havingStr.substring(0, havingStr.indexOf('<')).trim();
                } else if (havingStr.indexOf('!') > 0) {
                    changStr = havingStr.substring(0, havingStr.indexOf('!')).trim();
                } else if (havingStr.indexOf('=') > 0) {
                    changStr = havingStr.substring(0, havingStr.indexOf('=')).trim();
                } else if (lowerHaving.indexOf(" in") > 0) {
                    changStr = havingStr.substring(0, lowerHaving.indexOf(" in")).trim();
                } else if (lowerHaving.indexOf(" like") > 0) {
                    changStr = havingStr.substring(0, lowerHaving.indexOf(" like")).trim();
                }
                String columnField = buildHelper.getColumn(changStr);
                if (StringUtils.isBlank(columnField) || !buildHelper.checkEntityAttribute(changStr)) {
                    throw new QueryCriteriaException("Invalid having attribute: " + changStr);
                }
                havingStr = replaceFirstToken(havingStr, changStr, columnField);
            }
            havingColumns.add(havingStr);
        }
        if (!havingColumns.isEmpty()) {
            queryWrapper.having(String.join(QueryConstants.FIELD_DELIMITER, havingColumns));
        }
        return queryWrapper;
    }

    /**
     * 按顶层逗号拆分 having 子句，括号内的逗号不拆（避免 {@code age in (1,2,3)} 被拆坏）。
     */
    static List<String> splitHavingClauses(String havingParams) {
        List<String> parts = new ArrayList<>();
        if (havingParams == null) {
            return parts;
        }
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inQuote = false;
        for (int i = 0; i < havingParams.length(); i++) {
            char c = havingParams.charAt(i);
            if (c == '\'' && (i == 0 || havingParams.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
                current.append(c);
            } else if (!inQuote && c == '(') {
                depth++;
                current.append(c);
            } else if (!inQuote && c == ')') {
                depth = Math.max(0, depth - 1);
                current.append(c);
            } else if (!inQuote && c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    /**
     * 找到与 leftParen 位置匹配的右括号。
     */
    static int findMatchingParen(String s, int leftParen) {
        int depth = 0;
        boolean inQuote = false;
        for (int i = leftParen; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            } else if (!inQuote && c == '(') {
                depth++;
            } else if (!inQuote && c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 从函数参数中提取第一个字段标识符。
     * {@code created,'%Y'} → created；{@code ifnull(age,0)} → age；{@code age} → age
     */
    static String extractFieldToken(String inside) {
        if (inside == null) {
            return "";
        }
        String s = inside.trim();
        if (s.isEmpty()) {
            return "";
        }
        int topComma = indexOfTopLevelComma(s);
        if (topComma >= 0) {
            s = s.substring(0, topComma).trim();
        }
        int p = s.indexOf('(');
        if (p >= 0) {
            int close = findMatchingParen(s, p);
            if (close > p) {
                return extractFieldToken(s.substring(p + 1, close));
            }
        }
        return s.trim();
    }

    private static int indexOfTopLevelComma(String s) {
        int depth = 0;
        boolean inQuote = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            } else if (!inQuote && c == '(') {
                depth++;
            } else if (!inQuote && c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (!inQuote && c == ',' && depth == 0) {
                return i;
            }
        }
        return -1;
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
