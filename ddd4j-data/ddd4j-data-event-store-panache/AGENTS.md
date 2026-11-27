# ddd4j-data-event-store-panache

通过 Quarkus Panache 追加事件及参与外层事务。

Panache 追加既可能参与外层事务，也会遇到位置分配争用。调整重试分类时分开构造位置碰撞和聚合版本不匹配；只有允许重试的失败才进入下一次尝试。外层回滚时检查业务实体和整批事件是否一同回滚。

实现入口见 [PanacheEventStore](src/main/java/io/ddd4j/data/event/store/panache/PanacheEventStore.java)、[EventStoreRetry](src/main/java/io/ddd4j/data/event/store/panache/EventStoreRetry.java)。

## 回归

优先运行 [PanacheEventStoreRetryTest](src/test/java/io/ddd4j/data/event/store/panache/PanacheEventStoreRetryTest.java)、[JpaManagedParticipationIT](src/test/java/io/ddd4j/data/event/store/panache/JpaManagedParticipationIT.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-event-store-panache -am -Dtest=PanacheEventStoreRetryTest,JpaManagedParticipationIT -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-event-store-panache -am verify
```
