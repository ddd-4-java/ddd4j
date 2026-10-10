# javadoc 补全报告 — batch-04（ddd4j-core，30 个文件）

- 批次清单：`docs/verification/javadoc-fill-batches/batch-04.txt`
- 范围：`ddd4j-core` 主源码 6 个 + 测试 24 个
- HAZARD 行：5 条（3 个文件），已全部将 `{@link #xxx()}` 改为 `{@code xxx()}`
- 复查方式：脚本逐符号校验「清单中每个符号声明上方存在 javadoc 块」——30/30 文件、116/116 符号全部通过

## 逐文件结果

| 路径 | 处理符号数 | 状态 |
|---|---|---|
| ddd4j-core/src/main/java/io/ddd4j/core/subject/AnonymousSubjectProvider.java | 1 | 完成（已有 javadoc，复核通过；HAZARD×1 已修复） |
| ddd4j-core/src/main/java/io/ddd4j/core/subject/Subject.java | 20 | 完成（已有 javadoc，复核通过；HAZARD×3 已修复） |
| ddd4j-core/src/main/java/io/ddd4j/core/subject/SubjectDataProvider.java | 3 | 完成（已有 javadoc，复核通过） |
| ddd4j-core/src/main/java/io/ddd4j/core/subject/SubjectProvider.java | 3 | 完成（已有 javadoc，复核通过） |
| ddd4j-core/src/main/java/io/ddd4j/core/subject/SubjectStrategy.java | 5 | 完成（已有 javadoc，复核通过） |
| ddd4j-core/src/main/java/io/ddd4j/core/util/SubjectKit.java | 3 | 完成（已有 javadoc，复核通过；HAZARD×1 已修复） |
| ddd4j-core/src/test/java/io/ddd4j/core/context/SpiRegistrationScopeTest.java | 1 | 完成（新增 1 处） |
| ddd4j-core/src/test/java/io/ddd4j/core/cqrs/command/CommandHandlerTest.java | 2 | 完成（新增 2 处） |
| ddd4j-core/src/test/java/io/ddd4j/core/cqrs/command/CommandRegistryTest.java | 8 | 完成（新增 6 处，已有 2 处复核通过） |
| ddd4j-core/src/test/java/io/ddd4j/core/cqrs/eventstore/AsyncStoredEventTest.java | 2 | 完成（已有 javadoc，复核通过） |
| ddd4j-core/src/test/java/io/ddd4j/core/cqrs/eventstore/EventDeserializerTest.java | 3 | 完成（新增 3 处） |
| ddd4j-core/src/test/java/io/ddd4j/core/cqrs/eventstore/EventStoreContractTest.java | 5 | 完成（新增 5 处） |
| ddd4j-core/src/test/java/io/ddd4j/core/cqrs/eventstore/StoredEventStrongTypeContractTest.java | 2 | 完成（已有 javadoc，复核通过） |
| ddd4j-core/src/test/java/io/ddd4j/core/cqrs/eventstore/jackson/EventPayloadSerializerTest.java | 2 | 完成（已有 javadoc，复核通过） |
| ddd4j-core/src/test/java/io/ddd4j/core/cqrs/query/QueryConditionTest.java | 3 | 完成（新增 3 处） |
| ddd4j-core/src/test/java/io/ddd4j/core/cqrs/query/QueryRichMethodsTest.java | 2 | 完成（已有 javadoc，复核通过） |
| ddd4j-core/src/test/java/io/ddd4j/core/cqrs/readmodel/DefaultProjectionServiceTest.java | 4 | 完成（新增 4 处） |
| ddd4j-core/src/test/java/io/ddd4j/core/cqrs/readmodel/EventChunkReaderContractTest.java | 2 | 完成（新增 1 处，已有 1 处复核通过） |
| ddd4j-core/src/test/java/io/ddd4j/core/cqrs/readmodel/EventChunkTest.java | 3 | 完成（新增 3 处） |
| ddd4j-core/src/test/java/io/ddd4j/core/cqrs/readmodel/ProjectionMetricsTest.java | 5 | 完成（新增 3 处，已有 2 处复核通过） |
| ddd4j-core/src/test/java/io/ddd4j/core/cqrs/readmodel/ProjectionRunnerTest.java | 8 | 完成（新增 7 处，已有 1 处复核通过） |
| ddd4j-core/src/test/java/io/ddd4j/core/cqrs/readmodel/ProjectionStatusTest.java | 4 | 完成（新增 4 处） |
| ddd4j-core/src/test/java/io/ddd4j/core/cqrs/readmodel/TypedEventDispatcherTest.java | 2 | 完成（新增 2 处） |
| ddd4j-core/src/test/java/io/ddd4j/core/ddd/event/DomainEventJsonTest.java | 1 | 完成（新增 1 处） |
| ddd4j-core/src/test/java/io/ddd4j/core/ddd/event/DomainEventRoundTripTest.java | 1 | 完成（已有 javadoc，复核通过） |
| ddd4j-core/src/test/java/io/ddd4j/core/ddd/event/EntityIdPathTest.java | 2 | 完成（新增 2 处） |
| ddd4j-core/src/test/java/io/ddd4j/core/ddd/event/EventHandlerTest.java | 3 | 完成（新增 3 处） |
| ddd4j-core/src/test/java/io/ddd4j/core/ddd/model/AggregateRootApplyTest.java | 5 | 完成（新增 4 处，已有 1 处复核通过） |
| ddd4j-core/src/test/java/io/ddd4j/core/ddd/model/AggregateRootEventHandlerTest.java | 5 | 完成（新增 4 处，已有 1 处复核通过） |
| ddd4j-core/src/test/java/io/ddd4j/core/ddd/model/AggregateRootEventSourcingTest.java | 6 | 完成（新增 6 处） |

## 批次合计

- 文件数：30
- 清单符号总数：116（新增 javadoc 64 处，已有并复核通过 52 处）
- HAZARD 修复：5 条（AnonymousSubjectProvider 1、Subject 3、SubjectKit 1）
- 复查结果：30/30 文件通过，116/116 符号上方均存在 javadoc 块
