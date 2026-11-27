# ddd4j-runtime

容器注册、启动、停止及调度属于本组。公共行为由 runtime-testkit 提供断言；容器发现、资源所有权和销毁顺序要在目标框架验证。Javalin 使用 Guice 接线。

## 按变更选择模块

| 工件 | 修改范围 |
|---|---|
| [ddd4j-runtime-testkit](ddd4j-runtime-testkit/AGENTS.md) | 跨容器的服务注册、就绪状态和 CQRS 测试契约 |
| [ddd4j-runtime-spring](ddd4j-runtime-spring/AGENTS.md) | Spring Bean 发现、命令事务与投影调度 |
| [ddd4j-runtime-quarkus](ddd4j-runtime-quarkus/AGENTS.md) | Quarkus CDI 命令路由与上下文生命周期 |
| [ddd4j-runtime-guice](ddd4j-runtime-guice/AGENTS.md) | Guice 绑定、可选能力与 Javalin 运行时接线 |
| [ddd4j-runtime-micronaut](ddd4j-runtime-micronaut/AGENTS.md) | Micronaut Bean 工厂、投影调度及启停 |
| [ddd4j-runtime-vertx](ddd4j-runtime-vertx/AGENTS.md) | Vert.x EventBus 的本地分发、命令和投影生命周期 |
| [ddd4j-runtime-helidon](ddd4j-runtime-helidon/AGENTS.md) | Helidon MP 的 CDI 扩展、配置与显式投影 |
| [ddd4j-runtime-dropwizard](ddd4j-runtime-dropwizard/AGENTS.md) | Dropwizard Bundle 的资源注册、就绪检查与关闭 |
| [ddd4j-runtime-support](ddd4j-runtime-support/AGENTS.md) | 维护运行时就绪检查注册表。注册和清理必须保持贡献者归属；改动快照或聚合方式时，应观察多个贡献者组合后的就绪结果。 |

## 验证范围

从仓库根目录执行整组验证时使用下列命令。只改一个实现时，进入该模块指引选择对应的行为测试。

```sh
./mvnw -pl ddd4j-runtime -am verify
```
