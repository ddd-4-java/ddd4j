# Quarkus 投影迁移

默认模式为 `off`。仅提供 cron 或声明旧业务服务时，框架不会注册投影任务。

原先依靠 CDI 自动启动的应用应显式引入由 Quarkus BOM 管理的 `io.quarkus:quarkus-quartz`，并将同目录的 `application.properties` 配置加入业务应用。示例选择 `legacy`，每 30 秒运行一次旧投影。可将 cron 设为 `off` 或 `disabled` 以暂停调度。

需要持久化检查点时，设置 `ddd4j.quarkus.cqrs.projection.mode=durable`，提供业务事件读取器、持久化位置仓储或 EntityManager，以及 JTA 事务管理器。运行器在事务中处理事件和保存检查点。缺少必需组件时应用启动失败。

Java 8 应用没有原生定时方法时，用示例中的 `quarkus.quartz.start-mode=forced` 启动 Quarkus 管理的 Quartz。
