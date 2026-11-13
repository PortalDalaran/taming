package io.github.portaldalaran.taming.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * SQL 注入检测工具。
 * <p>
 * {@link #checkSqlInjection(String)} 用于字段名、orderBy、groupBy 等标识符/子句（不允许引号、注释、分号）；
 * {@link #checkSqlFragment(String)} 用于 fields/having/applySql/inSql/notInSql 等可含函数与引号字面量的场景，
 * 主要拦截注释、堆叠语句与 UNION SELECT。片段校验无法覆盖子查询内逻辑注入，调用方应尽量少用拼 SQL 算子。
 */
public class SqlUtils {

    /**
     * 等号后紧跟引号、注释或分号（含 URL 编码形式）
     */
    private static final Pattern EQ_THEN_ATTACK = Pattern.compile(
            "(?i)((%3D)|=)[^\\n]*((%27)|'|(\\-\\-)|(%3B)|;)");

    /**
     * 引号、行注释、块注释、分号（含 URL 编码）
     */
    private static final Pattern QUOTE_COMMENT_SEMI = Pattern.compile(
            "(?i)((%27)|'|(\\-\\-)|(/\\*)|(\\*/)|(%3B)|;)");

    /**
     * SQL 片段中的危险模式：注释、堆叠语句、UNION SELECT
     */
    private static final Pattern SQL_FRAGMENT_DANGER = Pattern.compile(
            "(?i)((\\-\\-)|(/\\*)|(\\*/)|(;\\s*(select|insert|update|delete|drop|alter|truncate|exec|execute|union))|(union(\\s+|\\+)select))");

    private SqlUtils() {
    }

    /**
     * 检测标识符/子句中的注入特征（不允许引号、注释、分号）。
     *
     * @param sqlStr 待检测字符串
     * @return true 表示疑似注入
     */
    public static boolean checkSqlInjection(String sqlStr) {
        if (StringUtils.isBlank(sqlStr)) {
            return false;
        }
        String compact = sqlStr.trim().replaceAll("\\s+", "");
        return EQ_THEN_ATTACK.matcher(compact).find()
                || QUOTE_COMMENT_SEMI.matcher(compact).find();
    }

    /**
     * 检测可含引号的 SQL 片段（applySql / inSql 等），拦截注释与堆叠查询。
     *
     * @param sqlFragment SQL 片段
     * @return true 表示疑似注入
     */
    public static boolean checkSqlFragment(String sqlFragment) {
        if (StringUtils.isBlank(sqlFragment)) {
            return false;
        }
        String compact = sqlFragment.trim().replaceAll("\\s+", " ");
        return SQL_FRAGMENT_DANGER.matcher(compact).find();
    }
}
