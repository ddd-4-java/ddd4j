# ddd4j-data-event-store-r2dbc

基于 R2DBC 连接的异步事件存储及同步入口。

把异步结果订阅到结束再检查数据库。过期版本应作为错误信号交给订阅者，失败批次不留下事件；按版本范围读取要区分同一 ID 下的不同聚合类型。同步入口与原生异步入口分别保留错误传播。

实现入口见 [R2dbcAsyncEventStore](src/main/java/io/ddd4j/data/event/store/r2dbc/R2dbcAsyncEventStore.java)、[R2dbcEventStore](src/main/java/io/ddd4j/data/event/store/r2dbc/R2dbcEventStore.java)。

## 回归

优先运行 [R2dbcAsyncEventStoreTest](src/test/java/io/ddd4j/data/event/store/r2dbc/R2dbcAsyncEventStoreTest.java)、[R2dbcEventStorePostgresIT](src/test/java/io/ddd4j/data/event/store/r2dbc/R2dbcEventStorePostgresIT.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-event-store-r2dbc -am -Dtest=R2dbcAsyncEventStoreTest,R2dbcEventStorePostgresIT -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-event-store-r2dbc -am verify
```

所列集成用例使用 Testcontainers。核对测试报告中对应测试类的执行数和跳过数；只运行单元用例时，单独记录仍缺的数据库或 broker 往返。

## 投影维护

维护投影位置的读取和持久化。改动查询、事务或列映射时，用同一标识先写入位置再重新读取，并核对查不到记录时的处理。

### 实现与调用

从 [R2dbcProjectionPositionRepository](src/main/java/io/ddd4j/data/projection/r2dbc/R2dbcProjectionPositionRepository.java) 进入。

修改 `R2dbcProjectionPositionRepository` 时，核对公开入口 `findByStreamIdReactive`、`findAllReactive`、`saveReactive`、`deleteByStreamIdReactive` 的调用方。

### 回归

- [ProjectionPositionR2dbcIT](src/test/java/io/ddd4j/data/projection/r2dbc/ProjectionPositionR2dbcIT.java) 中可从 `saveIncrReadBack_响应式完整循环_应跨仓储实例读回最终位置`、`resetToZero_响应式推进后重置_应回退到0` 定位断言。

- [ProjectionR2dbcModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/r2dbc/arch/ProjectionR2dbcModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-event-store-r2dbc -am -Dtest=ProjectionPositionR2dbcIT,ProjectionR2dbcModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-event-store-r2dbc -am verify
```
