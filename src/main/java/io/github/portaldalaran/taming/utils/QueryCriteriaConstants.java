package io.github.portaldalaran.taming.utils;

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
    public static final String ALL_EQ_OPERATOR = "allEq";
    public static final String EQ_OPERATOR = "eq";
    public static final String NE_OPERATOR = "ne";
    public static final String GE_OPERATOR = "ge";
    public static final String GT_OPERATOR = "gt";
    public static final String LE_OPERATOR = "le";
    public static final String LT_OPERATOR = "lt";
    public static final String LIKE_OPERATOR = "like";
    public static final String NOT_LIKE_OPERATOR = "notLike";
    public static final String START_WITH_OPERATOR = "startWith";
    public static final String END_WITH_OPERATOR = "endWith";
    public static final String IN_OPERATOR = "in";
    public static final String NOT_IN_OPERATOR = "notIn";

    public static final String BETWEEN_OPERATOR = "bet";
    public static final String SUM_OPERATOR = "sum";
    public static final String AVG_OPERATOR = "avg";
    public static final String COUNT_OPERATOR = "count";
    public static final String MIN_OPERATOR = "min";
    public static final String MAX_OPERATOR = "max";
}
