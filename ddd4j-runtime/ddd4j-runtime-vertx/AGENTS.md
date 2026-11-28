# ddd4j-runtime-vertx

Vert.x EventBus 的本地分发、命令和投影生命周期。

本地领域事件经 EventBus 发布，本地交付不要求网络 codec。改消息编码前先确定是否走跨进程链路；变更投影 timer 或 worker 后等待异步处理完成，再断言投影和关闭结果。

实现入口见 [Ddd4jVertxRuntime](src/main/java/io/ddd4j/vertx/Ddd4jVertxRuntime.java)、[VertxDomainEventPublisher](src/main/java/io/ddd4j/vertx/VertxDomainEventPublisher.java)。

保留的 `VertxProjectionScheduler.create(vertx, views, runner)` 工厂使用传入的 Vert.x。timer 触发后由 worker 执行投影，失败交给 Vert.x 异常处理器；关闭调度器会取消后续触发，调用方负责关闭 Vert.x。

## 回归

优先运行 [VertxDomainEventPublisherTest](src/test/java/io/ddd4j/vertx/VertxDomainEventPublisherTest.java)、[VertxCqrsRuntimeContractTest](src/test/java/io/ddd4j/vertx/cqrs/VertxCqrsRuntimeContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-vertx -am -Dtest=VertxDomainEventPublisherTest,VertxCqrsRuntimeContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-vertx -am verify
```
