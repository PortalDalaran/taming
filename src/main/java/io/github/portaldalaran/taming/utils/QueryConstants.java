package io.github.portaldalaran.taming.utils;

public class QueryConstants  {
    public static final String OR = "or";
    public static final String AND = "and";
    public static final String NESTED = "nested";
    /**
     * ex: {applySql: ['id > age']} ---> id > age
     * applySql: ["date_format(dateColumn,'%Y-%m-%d') = {0}", "2008-08-08"] --->date_format(dateColumn,'%Y-%m-%d') = '2008-08-08'")
     */
    public static final String APPLY_SQL = "applySql";
    public static final String GROUP_BY = "groupBy";
    public static final String HAVING = "having";
    public static final String ORDER_BY = "orderBy";
    public static final String FIELDS = "fields";
    public static final String PAGE_NO = "pageNo";
    public static final String PAGE_SIZE = "pageSize";
    public static final String DESC = "DESC";

    public static final String ASC = "ASC";
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
    public static final String EQ = "eq";
    /**
     * Not equal
     * ex: {name@ne: 'aron'} ---> name <> 'aron'
     */
    public static final String NE = "ne";
    /**
     * greater than or equal
     * ex: {age@ge: 12} ---> age >= 12
     */
    public static final String GE = "ge";
    /**
     * greater than
     * ex: {age@gt: 12} ---> age > 12
     */
    public static final String GT = "gt";
    /**
     * Less than or equal
     * ex: {age@le: 12} ---> age <= 12
     */
    public static final String LE = "le";
    /**
     * Less than
     * ex: {age@lt: 12} ---> age < 12
     */
    public static final String LT = "lt";
    /**
     * ex: {name@like: 'a'} ---> name like '%a%'
     */
    public static final String LIKE = "like";
    /**
     * ex: {name@notLike: 'a'} ---> name not like '%a%'
     */
    public static final String NOT_LIKE = "notLike";
    /**
     * ex: {name@startWith: 'a'} ---> name like 'a%'
     */
    public static final String START_WITH = "startWith";
    /**
     * ex: {name@endWith: 'a'} ---> name like '%a'
     */
    public static final String END_WITH = "endWith";
    /**
     * ex: {name@in: 'a,b,c'} ---> name in ('a','b','c')
     */
    public static final String IN = "in";

    /**
     * ex: {name@notIn: 'a,b,c'} ---> name not in ('a','b','c')
     */
    public static final String NOT_IN = "notIn";

    /**
     * ex: {id@inSql: 'select id from table where id < 3'} ---> id in (select id from table where id < 3)
     */
    public static final String IN_SQL = "inSql";
    /**
     * ex: {id@notInSql: 'select id from table where id < 3'} ---> id not in (select id from table where id < 3)
     */
    public static final String NOT_IN_SQL = "notInSql";


    public static final String BETWEEN = "bet";
    public static final String SUM = "sum";
    public static final String AVG = "avg";
    public static final String COUNT = "count";
    public static final String MIN = "min";
    public static final String MAX = "max";
}
