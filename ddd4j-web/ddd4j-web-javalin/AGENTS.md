# ddd4j-web-javalin

Javalin 处理器、失败事件及请求作用域。

## 实现与调用

从 [BaseHandler](src/main/java/io/ddd4j/web/javalin/BaseHandler.java)、[Ddd4jJavalinWeb](src/main/java/io/ddd4j/web/javalin/Ddd4jJavalinWeb.java)、[BodyParam](src/main/java/io/ddd4j/web/javalin/annotation/BodyParam.java) 进入。

修改 `Ddd4jJavalinWeb` 时，核对公开入口 `configure` 的调用方。

## 回归

- [Ddd4jJavalinWebContractTest](src/test/java/io/ddd4j/web/javalin/Ddd4jJavalinWebContractTest.java)。

- [Ddd4jJavalinOtelIntegrationTest](src/test/java/io/ddd4j/web/javalin/Ddd4jJavalinOtelIntegrationTest.java) 中可从 `javalinModuleCanUseWebOtelIntegration`、`javalinModuleCanUseWebOtelIntegration_withTraceparent` 定位断言。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-javalin -am -Dtest=Ddd4jJavalinWebContractTest,Ddd4jJavalinOtelIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-javalin -am verify
```
