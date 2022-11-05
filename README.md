驯服查询 taming

基于Mybatis Plus的QueryWrapper之上的查询条件拼装

Assembly of query conditions based on QueryWrapper of Mybatis Plus

# 1. summary
   以Key-Value方式传送Params，Key以@为间隔符，标明字段名称和操作。Value为查询值

   Params are transmitted in Key Value mode. Key uses @ as the separator to indicate the field name and operation. Value is the query value
```javascript
//示例 ex
{name@eq: 'david', age@gt: 18, or: { sex: 'male'}}
//等价于Sql Equivalent to Sql
(name = 'david' and age >= 18) or sex = 'male'
```

# 2. 操作符 Operator

## 2.1. 常规操作符 General operators

| 操作符    | 说明     | 示例 ex                                   | SQL                                            |
| --------- | -------- |-----------------------------------------|------------------------------------------------|
| eq        | 等于     | {id@eq:1,name@eq:"wang",age:null}       | id = 1 and name = 'wang' and age is null       |
| ne        | 不等于   | {id@ne:1,name@ne:"wang",age@ne:null}    | id != 1 and name != 'wang' and age is not null |
| ge        | 大于等于 | {id@ge:1,age@ge:18}                     | id >= 1 and  age >= 18                         |
| gt        | 大于     | {id@gt:1,age@gt:18}                     | id > 1 and  age > 18                           |
| le        | 小于等于 | {id@le:1,age@le:18}                     | id <= 1 and  age <= 18                         |
| lt        | 小于     | {id@lt:1,age@lt:18}                     | id < 1 and  age < 18                           |
| like      | 包含     | {name@like:"wang"}                      | name like '%wang%'                             |
| notLike   | 不包含   | {name@notLike:"wang"}                   | name not like '%wang%'                         |
| startWith | 左包含   | {name@startWith:"wang"}                 | name like '%wang'                              |
| endWith   | 右包含   | {name@endWith:"wang"}                   | name like 'wang%'                              |
| or        | 或者     | {id:1，or:{id:2,age:18}}                 | id=1 or (id=2 and age=18)                      |
| and       | 并且     | { id:1, and: { id: 2, or: {age: 18} } } | id=1 and (id=2 or age=18)                      |
| in        | 包括     | {id@in:"1,2,3"}                         | id in (1,2,3)                                  |
| notIn     | 不包括   | {id@notIn:"1,2,3"}                      | id not in (1,2,3)                              |
| bet       | 之间     | {age@bet:"19,30"}                       | age>19 and age<30                              |

## 2.2. 功能操作符 Function Operator

| 操作符  | 说明     | 示例 ex                                   | SQL                                        |
| ------- | -------- |-----------------------------------------| ------------------------------------------ |
| fields  | 显示字段 | { fields: "id, age, name, count(age)" } | select id,age,name,count(age) as age_count |
| orderBy | 排序     | { orderBy: "id@desc, name@asc" }        | order by id desc,name asc                  |
| groupBy | 分组     | { groupBy: "id,name" }                  | group by id,name                           |
| having  | 分组条件 | { having "sum(age) > 18" }              | having sum(age)>18                         |

Tips:

- groupBy字段会自动添加field_count到fields里。比如```{ groupBy: "id, name" }```在fields中有{fields: *, id_count, name_count, *}。
- The groupBy field will automatically add a field_ Count to fields. such as```{ groupBy: "id, name" }```There are {fields: *, id_count, name_count, *} in fields
- fields查询结果显示哪些字段，默认为所有，参考```select * from table```;
- The fields displayed in the query result are all by default. Refer to```select * from table```;

## 2.3. 统计操作符 Statistical operator

| 操作符 | 说明      | 示例                                 | SQL                                              |
| ------ | --------- | ------------------------------------ | ------------------------------------------------ |
| sum    | 汇总-求和 | { fields: "sum(id), sum(name)" }     | sum(id) as id_sum, sum(name) as name_sum         |
| count  | 汇总-计数 | { fields: "count(id), count(name)" } | count(id) as id_count, count(name) as name_count |
| avg    | 汇总-平均 | { fields: "avg(id), avg(name)" }     | avg(id) as id_avg, avg(name) as name_avg         |
| min    | 汇总-最小 | { fields: "min(id), min(name)" }     | min(id) as id_min, min(name) as name_min         |
| max    | 汇总-最大 | { fields: "max(id), max(name)" }     | max(id) as id_max, max(name) as name_max         |

# 3. 使用方式 Usage
- 
## 3.1. 定义POJO类

定义pojo超类，实体继承后，用于存储自定义查询条件的操作符。分为两个超类：一个是查询条件QueryCriteria，一个是分页查询条件PageCriteria

Define the pojo superclass. After the entity inherits, it is used to store the operator of user-defined query conditions. There are two superclasses: one is query criteria QueryCriteria, and the other is pagination query criteria PageCriteria

### 使用示例 Example of use

```java
@ApiModel("RequestVO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExPageReqVO extends PageCriteria {
    @ApiModelProperty(value = "user")
    private Long userId;

    @ApiModelProperty(value = "nickname")
    private String userNickname;
    
    //...
}
```
## 3.2 把自定义解析器 加入WebConfig  Add a custom parser to WebConfig

```java
@Configuration
public class ExWebAutoConfiguration implements WebMvcConfigurer {
    //....
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new QueryCriteriaMethodArgumentResolver());
        OrderComparator.sort(resolvers);
        WebMvcConfigurer.super.addArgumentResolvers(resolvers);
    }
    //....
}
```
## 3.4. 结合BaseMapper使用  Used in combination with BaseMapper

实例化QueryCriteriaWrapperBuilder要特别注册实例化范型，使用`new QueryCriteriaWrapperBuilder<ExDO>(){}`方式

To instantiate QueryCriteriaWrapperBuilder, you need to register the instantiation paradigm, using the 'new QueryCriteriaWrapperBuilder<ExDO>() {}' method

```java
	//...

	PageResult<ExDO> selectPage(ExPageReqVO reqVO) {
        //这里要用{} 初始化范型，不然Builder中取不到范型类型
        //Here, we need to initialize the paradigm with {}, otherwise we can't get the paradigm type in the Builder
        QueryCriteriaWrapperBuilder<ExDO> queryCriteriaWrapperBuilder = new QueryCriteriaWrapperBuilder<ExDO>(){};
        queryCriteriaWrapperBuilder.build(reqVO);
        return selectPage(reqVO.getPageParam(), queryCriteriaWrapperBuilder.getQueryWrapper());
    }

	//...
```

