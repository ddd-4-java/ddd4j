# ddd4j-samples

七种框架各有普通交易、CQRS、Sa-Token 和 Shiro 四种入口。它们共享商品、规格、订单和订单明细；框架负责启动、路由、命令总线与鉴权。改交易规则先在共享模块验证，再启动受影响的入口。

## 按变更选择模块

共享业务按以下顺序定位。

- [ddd4j-sample-order-domain](ddd4j-sample-order-domain/AGENTS.md) — 聚合、子项和仓储端口
- [ddd4j-sample-order-application](ddd4j-sample-order-application/AGENTS.md) — 命令、查询、事务编排和投影
- [ddd4j-sample-order-testkit](ddd4j-sample-order-testkit/AGENTS.md) — 跨框架 HTTP 与鉴权断言
- [ddd4j-sample-order-jdbc](ddd4j-sample-order-jdbc/AGENTS.md) — MyBatis 映射和四表初始化
- [ddd4j-sample-order-adapter](ddd4j-sample-order-adapter/AGENTS.md) — 共享 HTTP 与 CQRS 适配
- [ddd4j-sample-order-start](ddd4j-sample-order-start/AGENTS.md) — 运行时组装、真实 MySQL 和 COLA 回归

| 运行框架 | 普通交易 | CQRS | Sa-Token | Shiro |
|---|---|---|---|---|
| Spring | [交易路由](ddd4j-sample-spring/AGENTS.md) | [命令与投影](ddd4j-sample-spring-cqrs/AGENTS.md) | [Sa-Token 会话](ddd4j-sample-spring-satoken/AGENTS.md) | [Shiro 会话](ddd4j-sample-spring-shiro/AGENTS.md) |
| Quarkus | [交易路由](ddd4j-sample-quarkus/AGENTS.md) | [命令与投影](ddd4j-sample-quarkus-cqrs/AGENTS.md) | [Sa-Token 会话](ddd4j-sample-quarkus-satoken/AGENTS.md) | [Shiro 会话](ddd4j-sample-quarkus-shiro/AGENTS.md) |
| Javalin | [交易路由](ddd4j-sample-javalin/AGENTS.md) | [命令与投影](ddd4j-sample-javalin-cqrs/AGENTS.md) | [Sa-Token 会话](ddd4j-sample-javalin-satoken/AGENTS.md) | [Shiro 会话](ddd4j-sample-javalin-shiro/AGENTS.md) |
| Vert.x | [交易路由](ddd4j-sample-vertx/AGENTS.md) | [命令与投影](ddd4j-sample-vertx-cqrs/AGENTS.md) | [Sa-Token 会话](ddd4j-sample-vertx-satoken/AGENTS.md) | [Shiro 会话](ddd4j-sample-vertx-shiro/AGENTS.md) |
| Helidon MP | [交易路由](ddd4j-sample-helidon/AGENTS.md) | [命令与投影](ddd4j-sample-helidon-cqrs/AGENTS.md) | [Sa-Token 会话](ddd4j-sample-helidon-satoken/AGENTS.md) | [Shiro 会话](ddd4j-sample-helidon-shiro/AGENTS.md) |
| Micronaut | [交易路由](ddd4j-sample-micronaut/AGENTS.md) | [命令与投影](ddd4j-sample-micronaut-cqrs/AGENTS.md) | [Sa-Token 会话](ddd4j-sample-micronaut-satoken/AGENTS.md) | [Shiro 会话](ddd4j-sample-micronaut-shiro/AGENTS.md) |
| Dropwizard | [交易路由](ddd4j-sample-dropwizard/AGENTS.md) | [命令与投影](ddd4j-sample-dropwizard-cqrs/AGENTS.md) | [Sa-Token 会话](ddd4j-sample-dropwizard-satoken/AGENTS.md) | [Shiro 会话](ddd4j-sample-dropwizard-shiro/AGENTS.md) |

业务验收以 [四表商品交易规格](../docs/superpowers/specs/2026-11-25-catalog-trade-sample.md) 为准。数据库配置和 HTTP 请求示例见 [README.md](README.md)。

## 验证范围

从仓库根目录执行整组验证时使用下列命令。只改一个实现时，进入该模块指引选择对应的行为测试。

```sh
./mvnw -pl ddd4j-samples -am verify
```
