# ddd4j-data-jpa

JPA 聚合仓储、领域属性转换与加密字段监听。

聚合属性名和 JPA 持久化属性名可以不同。改条件翻译时分别以两种属性范围查询；改字段监听时用继承来的加密字段验证写入与恢复，并覆盖空值。两类问题要在各自的映射层解决。

实现入口见 [JpaAggregateRepository](src/main/java/io/ddd4j/data/jpa/JpaAggregateRepository.java)。

## 回归

优先运行 [JpaAggregateRepositoryCrudTest](src/test/java/io/ddd4j/data/jpa/JpaAggregateRepositoryCrudTest.java)、[JpaEncryptFieldListenerContractTest](src/test/java/io/ddd4j/data/jpa/crypto/JpaEncryptFieldListenerContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-jpa -am -Dtest=JpaAggregateRepositoryCrudTest,JpaEncryptFieldListenerContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-jpa -am verify
```
