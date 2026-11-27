# ddd4j-runtime-vertx

Vert.x EventBus 的本地分发、命令和投影生命周期。

## 实现与调用

从 [Ddd4jVertxRuntime](src/main/java/io/ddd4j/vertx/Ddd4jVertxRuntime.java)、[VertxDomainEventPublisher](src/main/java/io/ddd4j/vertx/VertxDomainEventPublisher.java)、[LocalEventMessageCodec](src/main/java/io/ddd4j/vertx/LocalEventMessageCodec.java) 进入。

修改 `Ddd4jVertxRuntime` 时，核对公开入口 `create`、`readiness`、`start`、`executeBlocking` 的调用方。

修改 `VertxDomainEventPublisher` 时，核对公开入口 `publish` 的调用方。

## 回归

- [VertxDomainEventPublisherTest](src/test/java/io/ddd4j/vertx/VertxDomainEventPublisherTest.java) 中可从 `shouldPublishLocalEventWithoutRequiringWireCodec` 定位断言。

- [Ddd4jVertxRuntimeTest](src/test/java/io/ddd4j/vertx/Ddd4jVertxRuntimeTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-vertx -am -Dtest=VertxDomainEventPublisherTest,Ddd4jVertxRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-vertx -am verify
```
