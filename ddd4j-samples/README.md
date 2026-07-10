# ddd4j 示例工程

本目录以当前 `ddd4j-samples/pom.xml` 为准，纳管 Spring、Quarkus、Javalin 三类运行时下的 DDD / CQRS / Auth
示例。历史的单点样例名不再作为文档口径或导航入口。

## 示例矩阵

| 示例                             | 方向     | 说明                                           |
|--------------------------------|--------|----------------------------------------------|
| `ddd4j-sample-spring`          | 普通 DDD | Order 充血聚合 + Goods 轻量 PO/Query，使用 Spring MVC |
| `ddd4j-sample-quarkus`         | 普通 DDD | 与 Spring 示例保持业务模型一致，使用 Quarkus CDI/JAX-RS    |
| `ddd4j-sample-javalin`         | 普通 DDD | 与 Spring 示例保持业务模型一致，使用 Guice/Javalin         |
| `ddd4j-sample-spring-cqrs`     | CQRS   | Spring 运行时下的 Order/Goods 读写分离示例              |
| `ddd4j-sample-quarkus-cqrs`    | CQRS   | Quarkus 运行时下的 Order/Goods 读写分离示例             |
| `ddd4j-sample-javalin-cqrs`    | CQRS   | Javalin 运行时下的 Order/Goods 读写分离示例             |
| `ddd4j-sample-spring-satoken`  | Auth   | Spring + Sa-Token 鉴权示例                       |
| `ddd4j-sample-spring-shiro`    | Auth   | Spring + Apache Shiro 鉴权示例                   |
| `ddd4j-sample-spring-security` | Auth   | Spring + Spring Security 鉴权示例                |
| `ddd4j-sample-quarkus-satoken` | Auth   | Quarkus + Sa-Token 鉴权示例                      |
| `ddd4j-sample-quarkus-shiro`   | Auth   | Quarkus + Apache Shiro 鉴权示例                  |
| `ddd4j-sample-javalin-satoken` | Auth   | Javalin + Sa-Token 鉴权示例                      |
| `ddd4j-sample-javalin-shiro`   | Auth   | Javalin + Apache Shiro 鉴权示例                  |

## 普通 DDD 示例

三个普通 DDD 示例使用同一组业务概念，重点不是复制 CRUD，而是对照三种运行时的差异边界：

| 业务轨道        | 示例对象                                                | 设计重点                                                    |
|-------------|-----------------------------------------------------|---------------------------------------------------------|
| 充血聚合        | `Order` / `OrderLine` / `Money` / `OrderRepository` | 聚合根封装状态机、不变量和领域事件，领域层不依赖具体 Web/DI/ORM                   |
| 轻量 PO/Query | `Goods` / `GoodsQuery` / `GoodsRepository`          | 简单 CRUD 场景保留轻量数据对象，查询能力通过 `Query` + `RichRepository` 承接 |

推荐先阅读：

```bash
mvn -pl ddd4j-samples/ddd4j-sample-spring -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-quarkus -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-javalin -am test -DskipTests=false
```

## CQRS 示例

CQRS 示例按运行时拆分，演示命令侧、查询侧、读模型和缓存的协作方式。当前样例名以运行时为前缀：

```bash
mvn -pl ddd4j-samples/ddd4j-sample-spring-cqrs -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-quarkus-cqrs -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-javalin-cqrs -am test -DskipTests=false
```

## Auth 示例

Auth 示例覆盖不同运行时和不同鉴权实现。业务层应优先面向 ddd4j 的 `Subject`/`SubjectProvider`/`AuthRequest` 契约；具体
sa-token、Shiro、
Spring Security 差异留在对应示例和适配层。

```bash
mvn -pl ddd4j-samples/ddd4j-sample-spring-satoken -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-spring-shiro -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-spring-security -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-quarkus-satoken -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-quarkus-shiro -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-javalin-satoken -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-javalin-shiro -am test -DskipTests=false
```

## 当前约束

- 本目录的权威清单是 `ddd4j-samples/pom.xml` 的 `<modules>`，README 不应再引用未纳管目录。
- Spring Boot 自动装配示例应放在外部 `ddd4j-boot` 仓库；本目录只演示 ddd4j 通用能力在不同运行时下的最小可运行方式。
- Quarkus/Javalin 专属脚手架能力分别归外部 `ddd4j-quarkus`、`ddd4j-javalin`，本目录只保留可对照的业务样例。
