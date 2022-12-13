package io.github.portaldalaran.taming.utils;

/**
 * @author aohee@163.com
 */
@Deprecated
public class QueryCriteriaConstants {
    public static final String OR_OPERATOR = "or";
    public static final String AND_OPERATOR = "and";
    /**
     * ex: {applySql: ['id > age']} ---> id > age
     * applySql: ["date_format(dateColumn,'%Y-%m-%d') = {0}", "2008-08-08"] --->date_format(dateColumn,'%Y-%m-%d') = '2008-08-08'")
     */
    public static final String APPLY_SQL_OPERATOR = "applySql";
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
    /**
     * Equal =
     * ex: {name@eq: 'aron'} ---> name = 'aron'
     * {name: 'aron'} --->  name = 'aron'
     */
    public static final String EQ_OPERATOR = "eq";
    /**
     * Not equal
     * ex: {name@ne: 'aron'} ---> name <> 'aron'
     */
    public static final String NE_OPERATOR = "ne";
    /**
     * greater than or equal
     * ex: {age@ge: 12} ---> age >= 12
     */
    public static final String GE_OPERATOR = "ge";
    /**
     * greater than
     * ex: {age@gt: 12} ---> age > 12
     */
    public static final String GT_OPERATOR = "gt";
    /**
     * Less than or equal
     * ex: {age@le: 12} ---> age <= 12
     */
    public static final String LE_OPERATOR = "le";
    /**
     * Less than
     * ex: {age@lt: 12} ---> age < 12
     */
    public static final String LT_OPERATOR = "lt";
    /**
     * ex: {name@like: 'a'} ---> name like '%a%'
     */
    public static final String LIKE_OPERATOR = "like";
    /**
     * ex: {name@notLike: 'a'} ---> name not like '%a%'
     */
    public static final String NOT_LIKE_OPERATOR = "notLike";
    /**
     * ex: {name@startWith: 'a'} ---> name like 'a%'
     */
    public static final String START_WITH_OPERATOR = "startWith";
    /**
     * ex: {name@endWith: 'a'} ---> name like '%a'
     */
    public static final String END_WITH_OPERATOR = "endWith";
    /**
     * ex: {name@in: 'a,b,c'} ---> name in ('a','b','c')
     */
    public static final String IN_OPERATOR = "in";

    /**
     * ex: {name@notIn: 'a,b,c'} ---> name not in ('a','b','c')
     */
    public static final String NOT_IN_OPERATOR = "notIn";

    /**
     * ex: {id@inSql: 'select id from table where id < 3'} ---> id in (select id from table where id < 3)
     */
    public static final String IN_SQL_OPERATOR = "inSql";
    /**
     * ex: {id@notInSql: 'select id from table where id < 3'} ---> id not in (select id from table where id < 3)
     */
    public static final String NOT_IN_SQL_OPERATOR = "notInSql";


    public static final String BETWEEN_OPERATOR = "bet";
    public static final String SUM_OPERATOR = "sum";
    public static final String AVG_OPERATOR = "avg";
    public static final String COUNT_OPERATOR = "count";
    public static final String MIN_OPERATOR = "min";
    public static final String MAX_OPERATOR = "max";
}
