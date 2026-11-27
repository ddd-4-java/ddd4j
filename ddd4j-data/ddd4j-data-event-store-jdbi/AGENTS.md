# ddd4j-data-event-store-jdbi

通过 JDBI Handle 追加事件、分配位置和恢复投影进度。

检查同一个 Handle 上的事务与位置分配。版本冲突要交回业务调用者，数据库争用按现有分类处理。改 JDBC 时间戳读写时分别设置数据库与 JVM 时区，再从数据库读回事件；在一个默认时区内自写自读容易掩盖偏移。

实现入口见 [JdbiEventStore](src/main/java/io/ddd4j/data/event/store/jdbi/JdbiEventStore.java)、[EventStoreRetry](src/main/java/io/ddd4j/data/event/store/jdbi/EventStoreRetry.java)。

## 回归

优先运行 [EventStoreRetryClassificationParityTest](src/test/java/io/ddd4j/data/event/store/jdbi/EventStoreRetryClassificationParityTest.java)、[JdbiEventStoreMysqlIT](src/test/java/io/ddd4j/data/event/store/jdbi/JdbiEventStoreMysqlIT.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-event-store-jdbi -am -Dtest=EventStoreRetryClassificationParityTest,JdbiEventStoreMysqlIT -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-event-store-jdbi -am verify
```

所列集成用例使用 Testcontainers。核对测试报告中对应测试类的执行数和跳过数；只运行单元用例时，单独记录仍缺的数据库或 broker 往返。
