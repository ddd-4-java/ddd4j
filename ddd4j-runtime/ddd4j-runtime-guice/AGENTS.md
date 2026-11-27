# ddd4j-runtime-guice

Guice 绑定、可选能力与 Javalin 运行时接线。

## 实现与调用

从 [Ddd4jGuiceModule](src/main/java/io/ddd4j/guice/Ddd4jGuiceModule.java)、[GuiceCommandBus](src/main/java/io/ddd4j/guice/command/GuiceCommandBus.java)、[GuiceI18nProvider](src/main/java/io/ddd4j/guice/i18n/GuiceI18nProvider.java) 进入。

修改 `Ddd4jGuiceModule` 时，核对公开入口 `basic`、`eventBus`、`projectionRunner`、`projectionService` 的调用方。

修改 `GuiceCommandBus` 时，核对公开入口 `execute` 的调用方。

## 回归

- [GuiceRuntimeContractTest](src/test/java/io/ddd4j/guice/GuiceRuntimeContractTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-guice -am -Dtest=GuiceRuntimeContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-guice -am verify
```
