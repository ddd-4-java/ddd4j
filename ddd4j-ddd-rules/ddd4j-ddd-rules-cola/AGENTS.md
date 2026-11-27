# ddd4j-ddd-rules-cola

COLA 包边界、允许的依赖及逆向依赖反例。

## 实现与调用

从 [ColaArchitectureChecker](src/main/java/io/ddd4j/ddd/cola/checker/ColaArchitectureChecker.java)、[ColaDDDLayerRules](src/main/java/io/ddd4j/ddd/cola/rules/ColaDDDLayerRules.java)、[ColaArchitectureTest](src/main/java/io/ddd4j/ddd/cola/test/ColaArchitectureTest.java) 进入。

修改 `ColaArchitectureChecker` 时，核对公开入口 `check` 的调用方。

修改 `ColaDDDLayerRules` 时，核对公开入口 `domainEntityInDomain`、`domainServiceInDomain`、`applicationServiceInApp`、`repositoryImplInAdapter` 的调用方。

## 回归

- [ColaArchitectureSelfTest](src/test/java/io/ddd4j/ddd/cola/ColaArchitectureSelfTest.java)。

```sh
./mvnw -pl ddd4j-ddd-rules/ddd4j-ddd-rules-cola -am -Dtest=ColaArchitectureSelfTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-ddd-rules/ddd4j-ddd-rules-cola -am verify
```
