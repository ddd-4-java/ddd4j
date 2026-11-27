# ddd4j-runtime-micronaut

Micronaut Bean 工厂、投影调度及启停。

基本上下文可在未启用投影时建立。改调度器要覆盖初次任务提交和后续任务交错；改启停时用部分启动失败与关闭两条路径确认视图资源释放、先前的 SPI 得到恢复。

实现入口见 [Ddd4jMicronautFactory](src/main/java/io/ddd4j/micronaut/Ddd4jMicronautFactory.java)、[Ddd4jMicronautRuntime](src/main/java/io/ddd4j/micronaut/Ddd4jMicronautRuntime.java)。

## 回归

优先运行 [MicronautProjectionSchedulerRaceTest](src/test/java/io/ddd4j/micronaut/cqrs/MicronautProjectionSchedulerRaceTest.java)、[MicronautCqrsRuntimeContractTest](src/test/java/io/ddd4j/micronaut/cqrs/MicronautCqrsRuntimeContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-micronaut -am -Dtest=MicronautProjectionSchedulerRaceTest,MicronautCqrsRuntimeContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-micronaut -am verify
```
