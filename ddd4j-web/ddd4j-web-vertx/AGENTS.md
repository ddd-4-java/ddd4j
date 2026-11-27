# ddd4j-web-vertx

Vert.x Router 请求上下文、提前响应与租约释放。

## 实现与调用

从 [Ddd4jVertxContext](src/main/java/io/ddd4j/web/vertx/Ddd4jVertxContext.java)、[Ddd4jVertxWeb](src/main/java/io/ddd4j/web/vertx/Ddd4jVertxWeb.java) 进入。

修改 `Ddd4jVertxContext` 时，核对公开入口 `request`、`subject`、`executeBlocking` 的调用方。

修改 `Ddd4jVertxWeb` 时，核对公开入口 `install`、`contextHandler`、`authenticationHandler`、`failureHandler` 的调用方。

## 回归

- [Ddd4jVertxWebContractTest](src/test/java/io/ddd4j/web/vertx/Ddd4jVertxWebContractTest.java)。

- [Ddd4jVertxContextTest](src/test/java/io/ddd4j/web/vertx/Ddd4jVertxContextTest.java) 中可从 `requestReturnsStoredContext`、`requestReturnsEmptyWhenAbsent` 定位断言。

- [Ddd4jVertxOtelIntegrationTest](src/test/java/io/ddd4j/web/vertx/Ddd4jVertxOtelIntegrationTest.java) 中可从 `vertxModuleCanUseWebOtelIntegration`、`vertxModuleCanUseWebOtelIntegration_withTraceparent` 定位断言。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-vertx -am -Dtest=Ddd4jVertxWebContractTest,Ddd4jVertxContextTest,Ddd4jVertxOtelIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-vertx -am verify
```
