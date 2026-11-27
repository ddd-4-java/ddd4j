# ddd4j-data-mybatisplus

MyBatis-Plus 聚合仓储、租户拦截与结果回填。

## 实现与调用

从 [Ddd4jTenantContext](src/main/java/io/ddd4j/data/mybatis/context/Ddd4jTenantContext.java)、[DataScopeProvider](src/main/java/io/ddd4j/data/mybatis/plugins/DataScopeProvider.java)、[MybatisAggregateRepository](src/main/java/io/ddd4j/data/mybatis/repository/MybatisAggregateRepository.java) 进入。

修改 `Ddd4jTenantContext` 时，核对公开入口 `clear`、`open`、`close` 的调用方。

## 回归

- [Ddd4jTenantContextTest](src/test/java/io/ddd4j/data/mybatis/adapter/Ddd4jTenantContextTest.java) 中可从 `shouldUseDdd4jThreadContextAsTenantSource`、`shouldWriteAndClearDdd4jThreadContext` 定位断言。

- [MybatisAggregateRepositoryCrudTest](src/test/java/io/ddd4j/data/mybatis/repository/MybatisAggregateRepositoryCrudTest.java) 中可从 `findById_shouldReturnModelWhenExists`、`findById_shouldReturnEmptyWhenNotExists` 定位断言。

- [MybatisAggregateRepositoryMySQLTest](src/test/java/io/ddd4j/data/mybatis/repository/MybatisAggregateRepositoryMySQLTest.java) 中可从 `saveInsertsNewAggregate`、`updateByIdUpdatesExistingOnly` 定位断言。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-mybatisplus -am -Dtest=Ddd4jTenantContextTest,MybatisAggregateRepositoryCrudTest,MybatisAggregateRepositoryMySQLTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-mybatisplus -am verify
```
