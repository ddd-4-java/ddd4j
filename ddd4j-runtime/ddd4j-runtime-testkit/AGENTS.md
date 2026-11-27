# ddd4j-runtime-testkit

跨框架复用的运行时测试夹具及断言。修改共享契约后，在调用它的框架模块确认装配结果。

## 实现与调用

从 [RuntimeContractAdapter](src/main/java/io/ddd4j/runtime/testkit/RuntimeContractAdapter.java)、[AbstractRuntimeContractTest](src/main/java/io/ddd4j/runtime/testkit/AbstractRuntimeContractTest.java)、[RuntimeContract](src/main/java/io/ddd4j/runtime/testkit/RuntimeContract.java) 进入。

修改 `RuntimeContractAdapter` 时，核对公开入口 `start`、`services`、`readiness`、`close` 的调用方。

## 回归

本工件没有独立测试类。修改公开接口后，另选实际消费模块执行行为回归。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-testkit -am verify
```
