# ddd4j-ddd-rules-cola

COLA 包边界、允许的依赖及逆向依赖反例。

修改 app/application、domain、adapter 或 infrastructure 匹配条件时，应以真实交易包和有意越界的依赖补充验证。验收要能指出越界的依赖；一个没有匹配到任何领域类的导入集合不能验证这项约束。

实现入口见 [ColaArchitectureChecker](src/main/java/io/ddd4j/ddd/cola/checker/ColaArchitectureChecker.java)、[ColaDDDLayerRules](src/main/java/io/ddd4j/ddd/cola/rules/ColaDDDLayerRules.java)。

## 回归

优先运行 [ColaArchitectureSelfTest](src/test/java/io/ddd4j/ddd/cola/ColaArchitectureSelfTest.java)、[ColaArchitectureStructureContractTest](src/test/java/io/ddd4j/ddd/cola/ColaArchitectureStructureContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-ddd-rules/ddd4j-ddd-rules-cola -am -Dtest=ColaArchitectureSelfTest,ColaArchitectureStructureContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-ddd-rules/ddd4j-ddd-rules-cola -am verify
```

共享示例的工件边界由 [CatalogArchitectureTest.java](../../ddd4j-samples/ddd4j-sample-order-start/src/test/java/io/ddd4j/sample/order/CatalogArchitectureTest.java) 执行。

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-order-start -am verify
```
