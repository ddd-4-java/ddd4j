# ddd4j-extension-qlexpress

QLExpress 执行选项、值转换与内建函数。

## 实现与调用

从 [QLExpressEngine](src/main/java/io/ddd4j/extension/qlexpress/QLExpressEngine.java)、[QLExpressEngineBuilder](src/main/java/io/ddd4j/extension/qlexpress/QLExpressEngineBuilder.java)、[QLExpress](src/main/java/io/ddd4j/extension/qlexpress/QLExpress.java) 进入。

修改 `QLExpressEngineBuilder` 时，核对公开入口 `builtInFunctions`、`allowPrivateAccess`、`traceExpression`、`securityStrategy` 的调用方。

## 回归

- [QLExpressEngineTest](src/test/java/io/ddd4j/extension/qlexpress/QLExpressEngineTest.java) 中可从 `executeShouldReturnExpressionValueInsteadOfNativeWrapper`、`builtInFunctionsShouldBeAvailableByDefault` 定位断言。

```sh
./mvnw -pl ddd4j-extensions/ddd4j-extension-qlexpress -am -Dtest=QLExpressEngineTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-extensions/ddd4j-extension-qlexpress -am verify
```
