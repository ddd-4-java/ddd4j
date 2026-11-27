# ddd4j-data-projection-panache

维护投影位置的读取和持久化。改动查询、事务或列映射时，用同一标识先写入位置再重新读取，并核对查不到记录时的处理。

## 实现与调用

从 [QuarkusProjectionPositionRepository](src/main/java/io/ddd4j/data/projection/panache/QuarkusProjectionPositionRepository.java)、[PanacheProjectionPositionEntity](src/main/java/io/ddd4j/data/projection/panache/PanacheProjectionPositionEntity.java) 进入。

修改 `QuarkusProjectionPositionRepository` 时，核对公开入口 `findByStreamId`、`findAll`、`save`、`deleteByStreamId` 的调用方。

修改 `PanacheProjectionPositionEntity` 时，核对公开入口 `upsert`、`resetToZero` 的调用方。

## 回归

- [QuarkusProjectionPositionRepositoryIT](src/test/java/io/ddd4j/data/projection/panache/QuarkusProjectionPositionRepositoryIT.java) 中可从 `findAll_返回可修改的独立结果快照`、`saveAdvanceReload_完整循环_应跨持久化上下文读回最终位置` 定位断言。

- [ProjectionRepositoryCdiContractTest](src/test/java/io/ddd4j/data/projection/panache/ProjectionRepositoryCdiContractTest.java) 中可从 `repositoryIsApplicationScoped` 定位断言。

- [ProjectionPanacheModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/panache/arch/ProjectionPanacheModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-projection-panache -am -Dtest=QuarkusProjectionPositionRepositoryIT,ProjectionRepositoryCdiContractTest,ProjectionPanacheModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-projection-panache -am verify
```
