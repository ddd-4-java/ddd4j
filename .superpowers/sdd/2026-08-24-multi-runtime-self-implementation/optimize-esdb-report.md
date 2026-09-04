# EsdbEventStore 实现报告

## 完成状态

已完成。commit `111cb4d1` 已提交到 `feature/3.0.x`（未 push）。

## 测试结果

| 类型 | 测试数 | 通过 | 跳过 | 失败 |
|------|--------|------|------|------|
| 单元测试 (EsdbEventStoreTest) | 11 | 11 | 0 | 0 |
| 集成测试 (EsdbEventStoreIT) | 6 | 0 | 6 | 0 |
| **合计** | **17** | **11** | **6** | **0** |

- 单元测试：Mockito mock `EventStoreDBClient`，验证事件映射、版本冲突翻译、流前缀
- 集成测试：`@Testcontainers(disabledWithoutDocker = true)`，无 Docker 时自动跳过

## 映射设计定案

| SPI 概念 | ESDB 映射 | 说明 |
|----------|-----------|------|
| stream 名 | `streamPrefix + aggregateId` | 直接使用，可选前缀做命名空间隔离 |
| expectedVersion | `ExpectedRevision` | `0` → `noStream()`，`N` → `expectedRevision(N-1)` |
| position | `Position.getCommitUnsigned()` | ESDB 全局 commitPosition，long 类型 |
| eventType | `EventData.eventType` | 事件类全限定名 |
| payload | `EventDataBuilder.json()` | `JsonKit.toJson()` 序列化 JSON |
| timestamp | `RecordedEvent.getCreated()` | `Instant` 类型 |
| 冲突异常 | `WrongExpectedVersionException` → `IllegalStateException` | 消息格式与 InMemoryEventStore 一致 |

## IT 镜像选择理由

**镜像**：`eventstore/eventstore:24.10.0-bookworm-slim`

选择理由：
- 2024 年 LTS 版本，基于 Debian Bookworm，体积小且稳定
- 支持 `INSECURE=true` 环境变量，单节点禁用 TLS，简化测试连接
- `EVENTSTORE_MEM_DB=true` 内存模式，测试隔离无残留
- 相比 21.10.0 旧版本，API 兼容性更好（db-client-java 5.4.5 对应 ESDB 23.10+）

## 偏差

- surefire 默认 `**/*Test.java` 模式不匹配 `*IT.java`，已显式添加 `<includes>` 配置（`**/*Test.java` + `**/*IT.java`）
- `RecordedEvent.getEventDataAs(Class)` 内部使用 Gson 反序列化（ESDB 客户端依赖），与 `JsonKit` 使用的 Jackson 不同，但结果语义一致

## 文件清单

- `ddd4j-data/ddd4j-data-event-store-esdb/pom.xml` — 添加 test 依赖 + surefire includes
- `ddd4j-data/ddd4j-data-event-store-esdb/src/main/java/io/ddd4j/data/event/store/esdb/EsdbEventStore.java` — 主实现
- `ddd4j-data/ddd4j-data-event-store-esdb/src/test/java/io/ddd4j/data/event/store/esdb/EsdbEventStoreTest.java` — 单元测试
- `ddd4j-data/ddd4j-data-event-store-esdb/src/test/java/io/ddd4j/data/event/store/esdb/EsdbEventStoreIT.java` — 集成测试
