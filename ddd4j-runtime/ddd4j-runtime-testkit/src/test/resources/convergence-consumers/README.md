# 七平台独立消费者夹具

每个平台的 `new` Maven 项目从隔离的本地 Maven 仓库解析当期 runtime。Javalin 使用 `ddd4j-runtime-guice`；接入 Javalin Web
时显式声明 Javalin 依赖。

投影装配使用各平台 runtime。持久投影需要同时声明实际使用的事件来源和位置存储。历史 CQRS
和投影坐标的验收结果见 [R13 和 R14 实施清单](../../../../../../docs/superpowers/plans/2026-10-10-three-line-product-convergence.md)。
