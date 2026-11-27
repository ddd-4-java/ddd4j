# ddd4j-ddd-rules-clean

Clean 分层的目录诊断与编译期类依赖检查。

## 实现与调用

从 [CleanArchitectureChecker](src/main/java/io/ddd4j/ddd/clean/checker/CleanArchitectureChecker.java)、[CleanDDDLayerRules](src/main/java/io/ddd4j/ddd/clean/rules/CleanDDDLayerRules.java)、[CleanArchitectureTest](src/main/java/io/ddd4j/ddd/clean/test/CleanArchitectureTest.java) 进入。

修改 `CleanArchitectureChecker` 时，核对公开入口 `check` 的调用方。

修改 `CleanDDDLayerRules` 时，核对公开入口 `domainEntityInDomain`、`domainServiceInDomain`、`applicationServiceInApp`、`repositoryImplInInfrastructure` 的调用方。

## 回归

- [CleanArchitectureSelfTest](src/test/java/io/ddd4j/ddd/clean/CleanArchitectureSelfTest.java)。

```sh
./mvnw -pl ddd4j-ddd-rules/ddd4j-ddd-rules-clean -am -Dtest=CleanArchitectureSelfTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-ddd-rules/ddd4j-ddd-rules-clean -am verify
```
