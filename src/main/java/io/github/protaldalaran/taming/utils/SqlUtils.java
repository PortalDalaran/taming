package io.github.protaldalaran.taming.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlUtils {
    /**
     * =号，单引号 --注释 分号   不扫描OR
     */
    private static final  String META_CHARACTERS = "/((%3D)|(=))[^\n]*((%27)|(')|(--)|(%3B)|(;))/";
    /**
     * 单引号或其十六进制等值
     */
    private static final  String TYPICAL_SQL = "/\\w*((%27)|(')|(%6F))/";

    /**
     * SQL注入检测
     *
     * @param sqlStr sql
     * @author wangxiaoli
     */
    public static boolean checkSqlInjection(String sqlStr) {
        //去掉空格
        sqlStr = sqlStr.trim().replaceAll(" ", "");
        Pattern pattern = Pattern.compile(META_CHARACTERS);
        Matcher matcher = pattern.matcher(sqlStr);
        if (matcher.find()) {
            return true;
        }
        Pattern tsPattern = Pattern.compile(TYPICAL_SQL);
        Matcher tsMatcher = tsPattern.matcher(sqlStr);
        return tsMatcher.find();
    }
}
