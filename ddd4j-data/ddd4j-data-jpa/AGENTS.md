# ddd4j-data-jpa

JPA 聚合仓储、领域属性转换与加密字段监听。

## 实现与调用

从 [JpaAggregateRepository](src/main/java/io/ddd4j/data/jpa/JpaAggregateRepository.java)、[AutoIncrementIdStrategy](src/main/java/io/ddd4j/data/jpa/AutoIncrementIdStrategy.java)、[IdGenerationStrategy](src/main/java/io/ddd4j/data/jpa/IdGenerationStrategy.java) 进入。

修改 `JpaAggregateRepository` 时，核对公开入口 `findById`、`save`、`deleteById`、`findByIds` 的调用方。

修改 `AutoIncrementIdStrategy` 时，核对公开入口 `generate` 的调用方。

## 回归

- [JpaAggregateRepositoryCrudTest](src/test/java/io/ddd4j/data/jpa/JpaAggregateRepositoryCrudTest.java) 中可从 `repository_shouldReturnEntityManager`、`persistenceProperty_shouldDelegateToSuper` 定位断言。

- [JpaAggregateRepositoryTest](src/test/java/io/ddd4j/data/jpa/JpaAggregateRepositoryTest.java) 中可从 `domainPropertyShouldUseRepositoryMapping`、`persistenceScopeShouldUseJpaPropertyDirectly` 定位断言。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-jpa -am -Dtest=JpaAggregateRepositoryCrudTest,JpaAggregateRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-jpa -am verify
```
