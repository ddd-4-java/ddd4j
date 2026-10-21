# 事件追加容量基线

三条发布线共用 `ddd4j-core` 的 `EventStoreAppendCapacityBaselineIT`。该测试显式运行，不参与默认构建。它先对每个指标预热 500 次，再采集 2000 个样本，并将本机结果写入 `ddd4j-core/target/three-line-parity/capacity/eventstore-append-baseline.json`。`target` 下的报告属于当前工作树的运行证据，不作为三线共用产品文档提交。

运行时使用对应发布线的 JDK，并传入准确的分支标识：

```bash
./mvnw -B -ntp -pl ddd4j-core -am \
  -Dtest=EventStoreAppendCapacityBaselineIT \
  -Dddd4j.baseline.branch=feature/1.0.x \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

在 `feature/2.0.x`、`feature/3.0.x` 上分别把 `ddd4j.baseline.branch` 改为对应值。测试拒绝缺失或无效的标识，报告同时记录 Java 版本、供应商、操作系统和测量时间。

| 指标 | 含义 |
| --- | --- |
| `eventstore.append.single.existing-stream` | 直连内存 `EventStore.append`，向已建流追加一个事件 |
| `eventstore.append.batch.10` | 批量追加十个事件的单事件摊销耗时 |
| `repository.append.enabled-path` | 经 `DefaultEventSourcingRepository#update` 的追加路径 |

这是手工 `System.nanoTime` 基线，不是 JMH 或吞吐测试。`overheadPercent` 只比较同次运行内的两条路径，不能证明复杂度、长期稳定性或跨 JDK 性能优劣。显著漂移应在同一机器、JDK、负载及构建条件下复测并分析。首版使用「超过同环境基线十倍时排查」作为告警线索，不把一次测量当成验收门槛。

历史参考：早期 `feature/1.0.x` 在 JDK 1.8.0_321、Windows 11 x64、24 核环境中记录过直连均值 1021.3 ns/op、仓储路径均值 1866.8 ns/op（约 +82.8%）。这些数字仅保留来源背景，不代表当前三线结果；当前结果以各线本次生成的 JSON 和测试日志为准。
