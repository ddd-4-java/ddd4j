# Quarkus 的旧投影显式配置

先在业务应用中显式引入 `io.quarkus:quarkus-quartz`，版本由该应用使用的 Quarkus BOM 管理。再把本目录 `application.properties` 中的 cron 项加入应用配置，可启用原有的每 30 秒轮询。已有 `ddd4j.quarkus.cqrs.projection.mode=legacy` 配置的应用也可沿用该默认表达式。默认 runtime 不会加载此示例。停用 `quarkus.scheduler.enabled` 或把 cron 设为 `off`、`disabled` 会停止启用旧投影。

- **Java 8 / Quarkus 1.13** 在默认 NORMAL 模式下，应用没有原生 `@Scheduled` 方法时可能没有启动平台 Quartz。显式开启旧投影的单机内存应用可以按需使用框架自建的 Quartz，随 runtime 一起关闭。持久化和集群模式使用应用的原生 Quartz，例如配置 `quarkus.quartz.start-mode=forced`。
- **Java 17 和 21 / Quarkus 3** 由 Quarkus 管理 Quartz。启用或关闭旧投影时，runtime 只在该调度器上新增或移除自己的任务。
