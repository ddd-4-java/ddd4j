# ddd4j-data-projection-jdbi

维护投影位置的读取和持久化。改动查询、事务或列映射时，用同一标识先写入位置再重新读取，并核对查不到记录时的处理。

## 实现与调用

从 [JdbiProjectionPositionRepository](src/main/java/io/ddd4j/data/projection/jdbi/JdbiProjectionPositionRepository.java) 进入。

修改 `JdbiProjectionPositionRepository` 时，核对公开入口 `findByStreamId`、`findAll`、`save`、`deleteByStreamId` 的调用方。

## 回归

- [ProjectionPositionJdbiIT](src/test/java/io/ddd4j/data/projection/jdbi/ProjectionPositionJdbiIT.java) 中可从 `saveIncrReload_完整循环_应跨仓储实例读回最终位置`、`resetToZero_推进后重置_应回退到0` 定位断言。

- [ProjectionJdbiModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/jdbi/arch/ProjectionJdbiModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-projection-jdbi -am -Dtest=ProjectionPositionJdbiIT,ProjectionJdbiModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-projection-jdbi -am verify
```
