# ddd4j-data-event-store-jpa

通过 EntityManager 持久化事件与投影位置。

先确认 EntityManager 的事务属于调用方还是仓储。外层回滚须连同已追加事件一起撤销，位置查询不能提交调用方事务。修改持久化注解、唯一约束或方言时，在受影响数据库验证旧版本追加失败后事件行数。

实现入口见 [JpaEventStore](src/main/java/io/ddd4j/data/event/store/jpa/JpaEventStore.java)、[JpaStoredEventRepositoryImpl](src/main/java/io/ddd4j/data/event/store/jpa/JpaStoredEventRepositoryImpl.java)。

## 回归

优先运行 [JpaManagedParticipationTest](src/test/java/io/ddd4j/data/event/store/jpa/JpaManagedParticipationTest.java)、[JpaEventStoreMysqlIT](src/test/java/io/ddd4j/data/event/store/jpa/JpaEventStoreMysqlIT.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-event-store-jpa -am -Dtest=JpaManagedParticipationTest,JpaEventStoreMysqlIT -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-event-store-jpa -am verify
```

所列集成用例使用 Testcontainers。核对测试报告中对应测试类的执行数和跳过数；只运行单元用例时，单独记录仍缺的数据库或 broker 往返。
