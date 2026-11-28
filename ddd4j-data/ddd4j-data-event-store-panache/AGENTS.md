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

## 投影维护

维护投影位置的读取和持久化。改动查询、事务或列映射时，用同一标识先写入位置再重新读取，并核对查不到记录时的处理。

### 实现与调用

从 [QuarkusProjectionPositionRepository](src/main/java/io/ddd4j/data/projection/panache/QuarkusProjectionPositionRepository.java)、[PanacheProjectionPositionEntity](src/main/java/io/ddd4j/data/projection/panache/PanacheProjectionPositionEntity.java) 进入。

修改 `QuarkusProjectionPositionRepository` 时，核对公开入口 `findByStreamId`、`findAll`、`save`、`deleteByStreamId` 的调用方。

修改 `PanacheProjectionPositionEntity` 时，核对公开入口 `upsert`、`resetToZero` 的调用方。

### 回归

- [QuarkusProjectionPositionRepositoryIT](src/test/java/io/ddd4j/data/projection/panache/QuarkusProjectionPositionRepositoryIT.java) 中可从 `findAll_返回可修改的独立结果快照`、`saveAdvanceReload_完整循环_应跨持久化上下文读回最终位置` 定位断言。

- [ProjectionRepositoryCdiContractTest](src/test/java/io/ddd4j/data/projection/panache/ProjectionRepositoryCdiContractTest.java) 中可从 `repositoryIsApplicationScoped` 定位断言。

- [ProjectionPanacheModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/panache/arch/ProjectionPanacheModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-event-store-panache -am -Dtest=QuarkusProjectionPositionRepositoryIT,ProjectionRepositoryCdiContractTest,ProjectionPanacheModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-event-store-panache -am verify
```
