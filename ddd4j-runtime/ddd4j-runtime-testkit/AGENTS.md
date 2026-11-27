# ddd4j-runtime-testkit

跨容器的服务注册、就绪状态和 CQRS 测试契约。

服务注册表交给消费方的是不可修改的快照。调整夹具时核对稳定的服务键和 readiness 列表。修改对外依赖时使用 src/test/resources/convergence-consumers 下对应框架的独立消费 POM，避免 reactor 的测试类路径掩盖缺失依赖。

实现入口见 [RuntimeContractAdapter](src/main/java/io/ddd4j/runtime/testkit/RuntimeContractAdapter.java)、[AbstractCqrsRuntimeContractTest](src/main/java/io/ddd4j/runtime/testkit/AbstractCqrsRuntimeContractTest.java)。

## 回归

优先运行 [RuntimeContractAdapterTest](src/test/java/io/ddd4j/runtime/testkit/RuntimeContractAdapterTest.java)、[RuntimeFixturesContractTest](src/test/java/io/ddd4j/runtime/testkit/RuntimeFixturesContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-testkit -am -Dtest=RuntimeContractAdapterTest,RuntimeFixturesContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-testkit -am verify
```
