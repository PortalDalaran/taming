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
| startWith | 左包含（前缀） | {name@startWith:"wang"}                 | name like 'wang%'                              |
| endWith   | 右包含（后缀） | {name@endWith:"wang"}                   | name like '%wang'                              |
| or        | 或者     | {id:1，or:{id:2,age:18}}                 | id=1 or (id=2 and age=18)                      |
| and       | 并且     | { id:1, and: { id: 2, or: {age: 18} } } | id=1 and (id=2 or age=18)                      |
| in        | 包括     | {id@in:"1,2,3"}                         | id in (1,2,3)                                  |
| notIn     | 不包括   | {id@notIn:"1,2,3"}                      | id not in (1,2,3)                              |
| bet       | 之间（闭区间） | {age@bet:"19,30"}                       | age between 19 and 30                          |

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
## 3.2 Spring Boot 自动配置 / Auto configuration

引入依赖后，默认自动注册 `QueryCriteriaMethodArgumentResolver`，一般无需手写 WebConfig。

After adding the dependency, `QueryCriteriaMethodArgumentResolver` is registered automatically.

```yaml
# 可选：关闭自动注册
taming:
  web:
    enabled: false
```

如需手动注册（或已关闭自动配置）：

```java
@Configuration
public class ExWebAutoConfiguration implements WebMvcConfigurer {
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new QueryCriteriaMethodArgumentResolver());
        OrderComparator.sort(resolvers);
    }
}
```
## 3.3. 加密参数传输  Encrypted parameter transmission

框架支持两种加密模式，算法由使用方自行实现 `BodyDecryptor` 接口后注入。

The framework supports two encryption modes. The algorithm is implemented by the caller via the `BodyDecryptor` interface.

### 模式一：整体 body 加密（POST application/json）

POST 请求的 body 为加密字符串，解密后应为标准 JSON 参数格式。

```
POST /users/list
Content-Type: application/json

<加密后的整体 JSON 字符串>
```

### 模式二：逐值加密（form-data / query string）

form-data 或 query string 中每个参数值单独加密，参数名保持明文。

```
GET /users/list?name%40like=<加密后的值>&age%40ge=<加密后的值>
```

### 配置步骤

**Step 1：实现 `BodyDecryptor` 接口**

```java
// 以 AES 为例，使用方自行实现解密逻辑
public class AesBodyDecryptor implements BodyDecryptor {
    private final String secretKey;

    public AesBodyDecryptor(String secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public String decrypt(String encrypted) {
        return AesUtils.decrypt(encrypted, secretKey);
    }
}
```

**Step 2：注册 `BodyDecryptor` Bean（自动配置会注入解析器）**

```java
@Configuration
public class EncryptConfiguration {
    @Bean
    public BodyDecryptor bodyDecryptor(@Value("${app.encrypt.secret-key}") String secretKey) {
        return new AesBodyDecryptor(secretKey);
    }
}
```

> 整体 body 加密会消费一次 `InputStream`；若下游仍需读取原始 body，请自行配合 `ContentCachingRequestWrapper` 等缓存方案。
> Full-body decrypt consumes the request InputStream once; cache the body if downstream still needs it.

**Step 3：Controller 参数上标注 `encrypted = true`**

```java
@GetMapping("/users")
public PageResult<UserDO> list(@RequestQueryParam(encrypted = true) UserPageReqVO reqVO) {
    return userService.selectPage(reqVO);
}
```

> **注意**
> - `encrypted = true` 但未注入 `BodyDecryptor` 时会抛出异常，须先注册解密 Bean。
> - 不使用加密时，`@RequestQueryParam` 用法与之前完全相同，无需任何改动。

## 3.4. 结合BaseMapper使用  Used in combination with BaseMapper

推荐使用静态工厂 `QueryCriteriaWrapperBuilder.of(Class)`，无需匿名子类。

Prefer `QueryCriteriaWrapperBuilder.of(Class)`; anonymous subclass is no longer required.

```java
	//...

	PageResult<ExDO> selectPage(ExPageReqVO reqVO) {
        QueryCriteriaWrapperBuilder<ExDO> queryCriteriaWrapperBuilder =
                QueryCriteriaWrapperBuilder.of(ExDO.class);
        queryCriteriaWrapperBuilder.build(reqVO);
        return selectPage(reqVO.getPageParam(), queryCriteriaWrapperBuilder.getQueryWrapper());
    }

	//...
```

兼容旧写法：`new QueryCriteriaWrapperBuilder<ExDO>(){}`（从泛型推断实体类型）。

