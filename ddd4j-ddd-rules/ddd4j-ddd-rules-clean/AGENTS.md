# ddd4j-ddd-rules-clean

Clean 分层的目录诊断与编译期类依赖检查。

目录检查与类依赖检查各有入口。调整期望目录会改变缺层诊断的先后次序和输出文本；调整 ArchUnit 规则则需要编译后的类作输入。分别用目录结构断言和有违规依赖的夹具验收。

实现入口见 [CleanArchitectureChecker](src/main/java/io/ddd4j/ddd/clean/checker/CleanArchitectureChecker.java)、[CleanDDDLayerRules](src/main/java/io/ddd4j/ddd/clean/rules/CleanDDDLayerRules.java)。

## 回归

优先运行 [CleanArchitectureSelfTest](src/test/java/io/ddd4j/ddd/clean/CleanArchitectureSelfTest.java)、[CleanArchitectureStructureContractTest](src/test/java/io/ddd4j/ddd/clean/CleanArchitectureStructureContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-ddd-rules/ddd4j-ddd-rules-clean -am -Dtest=CleanArchitectureSelfTest,CleanArchitectureStructureContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-ddd-rules/ddd4j-ddd-rules-clean -am verify
```
