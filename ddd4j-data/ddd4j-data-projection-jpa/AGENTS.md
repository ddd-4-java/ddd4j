# ddd4j-data-projection-jpa

维护投影位置的读取和持久化。改动查询、事务或列映射时，用同一标识先写入位置再重新读取，并核对查不到记录时的处理。

## 实现与调用

从 [JpaProjectionPositionRepository](src/main/java/io/ddd4j/data/projection/jpa/JpaProjectionPositionRepository.java)、[SpringDataProjectionPositionRepository](src/main/java/io/ddd4j/data/projection/jpa/SpringDataProjectionPositionRepository.java)、[ProjectionPositionEntity](src/main/java/io/ddd4j/data/projection/jpa/ProjectionPositionEntity.java) 进入。

修改 `JpaProjectionPositionRepository` 时，核对公开入口 `findByStreamId`、`findAll`、`save`、`deleteByStreamId` 的调用方。

## 回归

- [JpaProjectionPositionRepositoryTest](src/test/java/io/ddd4j/data/projection/jpa/JpaProjectionPositionRepositoryTest.java) 中可从 `findAllReturnsImmutablePositionValuesInRepositoryOrder`、`incrementByReportsTheStreamWhenUpdatedRowDisappearsBeforeReload` 定位断言。

- [ProjectionPositionJpaIT](src/test/java/io/ddd4j/data/projection/jpa/ProjectionPositionJpaIT.java) 中可从 `findAllReturnsAnImmutablePositionSnapshot`、`saveIncrReload_完整循环_应跨持久化上下文读回最终位置` 定位断言。

- [ProjectionJpaModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/jpa/arch/ProjectionJpaModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-projection-jpa -am -Dtest=JpaProjectionPositionRepositoryTest,ProjectionPositionJpaIT,ProjectionJpaModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-projection-jpa -am verify
```
