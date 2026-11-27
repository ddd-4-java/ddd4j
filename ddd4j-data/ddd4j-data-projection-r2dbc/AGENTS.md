# ddd4j-data-projection-r2dbc

维护投影位置的读取和持久化。改动查询、事务或列映射时，用同一标识先写入位置再重新读取，并核对查不到记录时的处理。

## 实现与调用

从 [R2dbcProjectionPositionRepository](src/main/java/io/ddd4j/data/projection/r2dbc/R2dbcProjectionPositionRepository.java) 进入。

修改 `R2dbcProjectionPositionRepository` 时，核对公开入口 `findByStreamIdReactive`、`findAllReactive`、`saveReactive`、`deleteByStreamIdReactive` 的调用方。

## 回归

- [ProjectionPositionR2dbcIT](src/test/java/io/ddd4j/data/projection/r2dbc/ProjectionPositionR2dbcIT.java) 中可从 `saveIncrReadBack_响应式完整循环_应跨仓储实例读回最终位置`、`resetToZero_响应式推进后重置_应回退到0` 定位断言。

- [ProjectionR2dbcModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/r2dbc/arch/ProjectionR2dbcModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-projection-r2dbc -am -Dtest=ProjectionPositionR2dbcIT,ProjectionR2dbcModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-projection-r2dbc -am verify
```
