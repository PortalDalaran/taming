package io.github.protaldalaran.taming.utils;

/**
 * @author aohee@163.com
 */
public class QueryCriteriaConstants {
    public static final String OR_OPERATOR = "or";
    public static final String AND_OPERATOR = "and";
    public static final String GROUP_BY_OPERATOR = "groupBy";
    public static final String HAVING_OPERATOR = "having";
    public static final String ORDER_BY_OPERATOR = "orderBy";
    public static final String FIELDS_OPERATOR = "fields";
    public static final String PAGE_NO = "pageNo";
    public static final String PAGE_SIZE = "pageSize";
    public static final String DESC_OPERATOR = "DESC";
    public static final String ASC_OPERATOR = "ASC";
    /**
     * Field separator，"@"
     * ex:{id@ne:1,name@like: "a"}
     */
    public static final String OPTION_DELIMITER = "@";
    /**
     * Field separator，","
     * ex:{id@ne:1,name@like: "a"}
     */
    public static final String FIELD_DELIMITER = ",";
    /**
     * Associative Object Separator，"."
     * ex:{user.id@ne:1,user.name@like: "a"}
     */
    public static final String RELATION_DELIMITER = ".";

}
