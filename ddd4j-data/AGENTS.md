# ddd4j-data

普通聚合的查询与更新进入所选 ORM；事件流的追加、范围读取和全局位置进入相应 event-store 模块。两个入口可能使用同一种数据库，事务和并发承诺各自独立。

## 按变更选择模块

| 工件 | 修改范围 |
|---|---|
| [ddd4j-data-crypto](ddd4j-data-crypto/AGENTS.md) | 字段加解密处理器与本地、远端策略 |
| [ddd4j-data-event-store-jpa](ddd4j-data-event-store-jpa/AGENTS.md) | 通过 EntityManager 持久化事件与投影位置 |
| [ddd4j-data-event-store-panache](ddd4j-data-event-store-panache/AGENTS.md) | 通过 Quarkus Panache 追加事件及参与外层事务 |
| [ddd4j-data-event-store-jdbi](ddd4j-data-event-store-jdbi/AGENTS.md) | 通过 JDBI Handle 追加事件、分配位置和恢复投影进度 |
| [ddd4j-data-event-store-esdb](ddd4j-data-event-store-esdb/AGENTS.md) | EventStoreDB 原生事件流、期望版本和全局位置分页 |
| [ddd4j-data-event-store-r2dbc](ddd4j-data-event-store-r2dbc/AGENTS.md) | 基于 R2DBC 连接的异步事件存储及同步入口 |
| [ddd4j-data-external](ddd4j-data-external/AGENTS.md) | 地理、地区、天气与序列号服务适配 |
| [ddd4j-data-jpa](ddd4j-data-jpa/AGENTS.md) | JPA 聚合仓储、领域属性转换与加密字段监听 |
| [ddd4j-data-logs](ddd4j-data-logs/AGENTS.md) | API 操作日志切面、请求上下文和日志提供者 |
| [ddd4j-data-mybatis](ddd4j-data-mybatis/AGENTS.md) | 原生 MyBatis 的仓储、租户上下文与 SQL 观察 |
| [ddd4j-data-mybatisplus](ddd4j-data-mybatisplus/AGENTS.md) | MyBatis-Plus 聚合仓储、租户拦截与结果回填 |

## 验证范围

从仓库根目录执行整组验证时使用下列命令。只改一个实现时，进入该模块指引选择对应的行为测试。

```sh
./mvnw -pl ddd4j-data -am verify
```
