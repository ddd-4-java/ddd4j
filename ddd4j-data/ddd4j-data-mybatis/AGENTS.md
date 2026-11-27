# ddd4j-data-mybatis

原生 MyBatis 的仓储、租户上下文与 SQL 观察。

## 实现与调用

从 [Ddd4jTenantContext](src/main/java/io/ddd4j/data/mybatis/context/Ddd4jTenantContext.java)、[DataScopeProvider](src/main/java/io/ddd4j/data/mybatis/plugins/DataScopeProvider.java)、[MybatisAggregateRepository](src/main/java/io/ddd4j/data/mybatis/repository/MybatisAggregateRepository.java) 进入。

修改 `Ddd4jTenantContext` 时，核对公开入口 `clear`、`open`、`close` 的调用方。

## 回归

- [Ddd4jSqlObservationSinkTest](src/test/java/io/ddd4j/data/mybatis/adapter/Ddd4jSqlObservationSinkTest.java) 中可从 `shouldStoreSqlObservationInThreadContext`、`shouldBeDiscoverableThroughServiceLoader` 定位断言。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-mybatis -am -Dtest=Ddd4jSqlObservationSinkTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-mybatis -am verify
```
