# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目简介

**Taming** 是一个基于 MyBatis Plus 的 QueryWrapper 扩展库（`io.github.portaldalaran:taming`），允许以 Key-Value 方式通过 HTTP 请求传递查询参数，并自动构建 MyBatis Plus 的 `QueryWrapper`。字段名与操作符之间使用 `@` 作为分隔符。

GitHub: https://github.com/PortalDalaran/taming

## 构建命令

```bash
# 编译打包
mvn clean package

# 安装到本地仓库
mvn clean install

# 发布到 Maven Central（需要 GPG 密钥 0xCD002CBA）
mvn deploy
```

**技术栈**: Java 17, Maven 3.9+, Spring Boot 3.5.x, MyBatis Plus 3.5.x, Jackson, Lombok

## 运行测试

```bash
mvn test
```

测试文件位于 `src/test/java/io/github/portaldalaran/taming/`。

## 架构概览

### 核心流程

```
HTTP 请求参数（form data 或 JSON）
    ↓
@RequestQueryParam 注解（Spring MVC Controller 参数）
    ↓
QueryCriteriaMethodArgumentResolver（Spring MVC 参数解析器）
    ↓
QueryCriteriaDataBinder（解析 field@operation 语法）
    ↓
QueryCriteria<T> / PageCriteria<T>（查询条件 POJO）
    ↓
QueryCriteriaWrapperBuilder.of(Entity.class).build(...)（主入口）
    ↓
各 Builder 类（Where/Columns/Group/Order）
    ↓
QueryWrapper<T>（MyBatis Plus）
```

### 包结构职责

- **`annotation/`** — `@RequestQueryParam`（Controller 参数注解）、`@TamingOperation`（操作符标注）
- **`pojo/`** — `QueryCriteria<T>`（查询基类）、`PageCriteria<T>`（分页基类，含 pageNo/pageSize）、`QueryCriteriaParam<T>`（单个查询条件）
- **`core/`** — Spring MVC 集成：参数解析器、数据绑定器、模型属性处理器
- **`autoconfigure/`** — Spring Boot 自动配置（注册 ArgumentResolver）
- **`mybatisplus/`** — `QueryCriteriaWrapperBuilder<T>`（核心入口，推荐 `of(Class)`）
- **`builder/`** — 各 SQL 子句构建器（WHERE、SELECT、GROUP BY、ORDER BY）
- **`utils/`** — SQL 注入检查、JSON 工具、常量定义等

### 关键实现细节

**推荐使用静态工厂**（无需匿名子类）：

```java
QueryCriteriaWrapperBuilder<UserDO> builder = QueryCriteriaWrapperBuilder.of(UserDO.class);
builder.build(reqVO);
QueryWrapper<UserDO> queryWrapper = builder.getQueryWrapper();
```

仍支持匿名子类以从泛型推断实体类型：`new QueryCriteriaWrapperBuilder<UserDO>(){}`。

**参数语法格式**（`QueryCriteriaDataBinder` 负责解析）：
- 单条件：`{"name@like": "zhang", "age@ge": 18}`
- OR 条件：`{"id": 1, "or": {"id": 2, "age": 18}}` → `id=1 OR (id=2 AND age=18)`
- 分页：`{"pageNo": 1, "pageSize": 10}`
- 排序：`{"orderBy": "id@desc,name@asc"}`

**支持的操作符**（定义在 `QueryConstants`）：
`eq`, `ne`, `ge`, `gt`, `le`, `lt`, `like`, `notLike`, `startWith`, `endWith`, `in`, `notIn`, `bet`（between）, `null`, `notNull`, `inSql`, `notInSql`, `applySql`

**SQL 注入防护**：字段名 / orderBy / groupBy 做标识符级校验；`fields` / `having` / `applySql` / `inSql` / `notInSql` 走片段级校验（拦注释、堆叠语句、UNION）。片段校验无法覆盖子查询内逻辑注入，生产环境应慎用拼 SQL 算子。普通条件 value 由 MyBatis-Plus 参数绑定。字段名必须是 `QueryCriteria` 子类中已声明的属性。

**`BuildHelper<T>`** 负责实体字段元数据提取和字段名到数据库列名的映射，是各 Builder 的核心依赖。

**自动配置**：引入依赖后默认注册 `QueryCriteriaMethodArgumentResolver`；可用 `taming.web.enabled=false` 关闭。`@RequestQueryParam(encrypted = true)` 时必须提供 `BodyDecryptor` Bean，否则抛异常。

### 与 `talons` 的依赖关系

项目依赖自定义库 `io.github.portaldalaran:talons`（与本库同主版本），该库提供关联查询等基础能力。Spring / MyBatis-Plus 依赖为 `provided`，由使用方提供。
