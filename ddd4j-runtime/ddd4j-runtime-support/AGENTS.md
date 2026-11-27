# ddd4j-runtime-support

维护运行时就绪检查注册表。注册和清理必须保持贡献者归属；改动快照或聚合方式时，应观察多个贡献者组合后的就绪结果。

## 实现与调用

从 [RuntimeReadinessRegistry](src/main/java/io/ddd4j/runtime/health/RuntimeReadinessRegistry.java) 进入。

修改 `RuntimeReadinessRegistry` 时，核对公开入口 `register`、`registerAll`、`unregister`、`readiness` 的调用方。

## 回归

- [RuntimeReadinessRegistryTest](src/test/java/io/ddd4j/runtime/health/RuntimeReadinessRegistryTest.java) 中可从 `shouldAggregateContributorsAndAllowRuntimeRegistrationChanges`、`shouldFilterNullCollectionAndElementsInRegisterAll` 定位断言。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-support -am -Dtest=RuntimeReadinessRegistryTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-support -am verify
```
