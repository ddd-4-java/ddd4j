# ddd4j-extension-qlexpress

QLExpress 执行选项、值转换与内建函数。

调用者取得表达式计算值。调整执行包装时保持该返回类型，并检查原生 attachments 和执行选项是否继续传入引擎；默认内建函数的注册与用户追加函数要能共存。

实现入口见 [QLExpressEngine](src/main/java/io/ddd4j/extension/qlexpress/QLExpressEngine.java)、[QLExpressEngineBuilder](src/main/java/io/ddd4j/extension/qlexpress/QLExpressEngineBuilder.java)。

## 回归

优先运行 [QLExpressEngineTest](src/test/java/io/ddd4j/extension/qlexpress/QLExpressEngineTest.java)、[QLExpressExecutionOptionsContractTest](src/test/java/io/ddd4j/extension/qlexpress/model/QLExpressExecutionOptionsContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-extensions/ddd4j-extension-qlexpress -am -Dtest=QLExpressEngineTest,QLExpressExecutionOptionsContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-extensions/ddd4j-extension-qlexpress -am verify
```
