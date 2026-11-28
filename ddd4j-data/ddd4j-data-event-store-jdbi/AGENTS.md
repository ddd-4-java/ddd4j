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

## 投影维护

维护投影位置的读取和持久化。改动查询、事务或列映射时，用同一标识先写入位置再重新读取，并核对查不到记录时的处理。

### 实现与调用

从 [JdbiProjectionPositionRepository](src/main/java/io/ddd4j/data/projection/jdbi/JdbiProjectionPositionRepository.java) 进入。

修改 `JdbiProjectionPositionRepository` 时，核对公开入口 `findByStreamId`、`findAll`、`save`、`deleteByStreamId` 的调用方。

### 回归

- [ProjectionPositionJdbiIT](src/test/java/io/ddd4j/data/projection/jdbi/ProjectionPositionJdbiIT.java) 中可从 `saveIncrReload_完整循环_应跨仓储实例读回最终位置`、`resetToZero_推进后重置_应回退到0` 定位断言。

- [ProjectionJdbiModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/jdbi/arch/ProjectionJdbiModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-event-store-jdbi -am -Dtest=ProjectionPositionJdbiIT,ProjectionJdbiModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-event-store-jdbi -am verify
```
