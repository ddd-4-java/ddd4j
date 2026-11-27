# ddd4j-data

普通聚合的查询与更新进入所选 ORM；命令路由与数据权限分别由本线 CQRS、datascope 适配模块维护。

## 按变更选择模块

| 工件 | 修改范围 |
|---|---|
| [ddd4j-data-crypto](ddd4j-data-crypto/AGENTS.md) | 字段加解密处理器与本地、远端策略 |
| [ddd4j-data-external](ddd4j-data-external/AGENTS.md) | 地理、地区、天气与序列号服务适配 |
| [ddd4j-data-jpa](ddd4j-data-jpa/AGENTS.md) | JPA 聚合仓储、领域属性转换与加密字段监听 |
| [ddd4j-data-logs](ddd4j-data-logs/AGENTS.md) | API 操作日志切面、请求上下文和日志提供者 |
| [ddd4j-data-mybatis](ddd4j-data-mybatis/AGENTS.md) | 原生 MyBatis 的仓储、租户上下文与 SQL 观察 |
| [ddd4j-data-mybatisplus](ddd4j-data-mybatisplus/AGENTS.md) | MyBatis-Plus 聚合仓储、租户拦截与结果回填 |
| [ddd4j-data-cqrs-dropwizard](ddd4j-data-cqrs-dropwizard/AGENTS.md) | 维护 dropwizard 的命令分发。改动执行器注册或调用方式时，从本模块的命令总线追到 core 的 CommandRegistry 和返回对象，再验证对应框架的实际装配。 |
| [ddd4j-data-cqrs-micronaut](ddd4j-data-cqrs-micronaut/AGENTS.md) | 维护 micronaut 的命令分发。改动执行器注册或调用方式时，从本模块的命令总线追到 core 的 CommandRegistry 和返回对象，再验证对应框架的实际装配。 |
| [ddd4j-data-cqrs-vertx](ddd4j-data-cqrs-vertx/AGENTS.md) | 维护 vertx 的命令分发。改动执行器注册或调用方式时，从本模块的命令总线追到 core 的 CommandRegistry 和返回对象，再验证对应框架的实际装配。 |
| [ddd4j-data-datascope](ddd4j-data-datascope/AGENTS.md) | 维护数据范围权限注解、校验器和提供者。调整授权逻辑时覆盖没有提供者、没有权限和已授权三种情况，验证拒绝路径。 |

## 验证范围

从仓库根目录执行整组验证时使用下列命令。只改一个实现时，进入该模块指引选择对应的行为测试。

```sh
./mvnw -pl ddd4j-data -am verify
```
