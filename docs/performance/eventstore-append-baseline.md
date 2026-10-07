# 事件追加容量基线对比说明

- 基线文件：`docs/performance/eventstore-append-baseline.json`（由 `ddd4j-core` 的 `EventStoreAppendCapacityBaselineIT` 以 `-Dtest=...` 显式运行产出）。
- 测量环境：JDK 1.8.0_321 / Windows 11 x64 / 24 核，feature/1.0.x。
- 方法学：手工 nanoTime 基线（非 JMH）——每指标预热 500 次 + 正式采样 2000 次，先整轮预热一轮并丢弃以消除 JIT 次序偏差。用于**版本间回归对比**，不用于绝对值宣称。

## 指标口径

| 指标 | 含义 |
|---|---|
| `eventstore.append.single.existing-stream` | 直连 `EventStore.append`（已建流，期望版本 1 → 2），能力未启用路径 |
| `eventstore.append.batch.10` | 批量 10 事件追加的单事件摊销 |
| `repository.append.enabled-path` | 经 `DefaultEventSourcingRepository#update` 的追加（能力启用路径） |

## 与启用前的对比

直连 `EventStore.append` 均值 1021.3 ns/op，经 `DefaultEventSourcingRepository` 为 1866.8 ns/op：能力启用路径每次追加多承担约 **+82.8%（绝对值 ≈ +846 ns）**，来源为版本跟踪 Map 读写、`pullDomainEvents` 未提交事件解耦与 `AggregateRootId` 适配——常量级差异，不随事件流长度增长；换来的是乐观并发冲突拒绝与重放重建语义。后续版本以本文件为基线对比，`overheadPercent` 显著漂移需回归排查。

## 劣化阈值校准（首版）

- 与基线**同数量级**（≤10×基线）：正常波动，记录即可；
- 超过 **10 倍劣化**：告警并回归排查（追加路径出现非预期 IO/锁竞争/分配）。
