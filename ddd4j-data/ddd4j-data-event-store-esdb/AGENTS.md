# ddd4j-data-event-store-esdb

EventStoreDB 原生事件流、期望版本和全局位置分页。

本线 read 从流起点读取一个受限批次，范围重载在结果中按闭区间筛选。readAll 从指定位置读取一批记录，再按前缀和系统流过滤。修改跨页或恢复行为时需补真实 ESDB 用例，并核对消费者是否收到完整区间。

实现入口见 [EsdbEventStore](src/main/java/io/ddd4j/data/event/store/esdb/EsdbEventStore.java)。

## 回归

优先运行 [EsdbEventStoreIT](src/test/java/io/ddd4j/data/event/store/esdb/EsdbEventStoreIT.java)、[EsdbEventStoreTest](src/test/java/io/ddd4j/data/event/store/esdb/EsdbEventStoreTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-event-store-esdb -am -Dtest=EsdbEventStoreIT,EsdbEventStoreTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-event-store-esdb -am verify
```

所列集成用例使用 Testcontainers。核对测试报告中对应测试类的执行数和跳过数；只运行单元用例时，单独记录仍缺的数据库或 broker 往返。
