# ADR-0005: EventStore 事件存储 SPI 设计

## Status

Accepted（2026-08-24）

> 相关：ADR-0001（不集成 esc-api 的许可证理由）、ADR-0003（四套持久化实现）、ADR-0004（冲突异常同风格）；证据见 ../reference/fuin-api-patterns/05-event-store.md、../reference/fuin-api-patterns/04-event-sourcing-repository.md、../reference/fuin-api-patterns/03-domain-event.md。

## Context

事件存储的参照系是外部构件 esc-api 0.9.0（`org.fuin.esc:esc-api`，非 ddd-4-java 本体；../reference/fuin-api-patterns/05-event-store.md）。其 SPI 的四类负担构成本决策的问题陈述：

- **同步／异步双轨全量复制**（05 篇「缺点」节）：
  - `EventStoreAsync` 与 `EventStore` 完全平行，Writable／Readable 各配一份 CompletableFuture 版，约 20 个签名×2，漂移风险高；
  - `DelegatingAsyncEventStore` 用线程池包同步实现，异步名不副实。
- **软删无墓碑**（05 篇；04 篇已判定删除应走墓碑领域事件）：
  - `deleteStream` 是存储管理命令而非事件追加，投影／订阅者无从感知删除发生；
  - 软删后重建流版本号接着软删前继续，下游易误读流历史。
- **EJB 时代遗留与生命周期入 SPI**（05 篇）：
  - `open()/close()`＋「隐式 open」约定掩盖资源泄漏；
  - `ReadableEventStore` 尚存 ejb-jar.xml 配置异常的 CAUTION。
- **无全局顺序**（05 篇）：
  - 读 API 全部按流定位，无全局 position；
  - 投影断线续传需自管位点，跨流订阅只能靠 volatile 订阅或投影流。

同时 esc-api 有四点已验证可取（05 篇「优点」节）：

- 切片循环协议：起点＋数量→结果带 nextEventNumber 游标，调用方无需预知事件总数；
- 追加后一致性断言：expectedVersion＋事件数 == nextVersion；
- 乐观锁异常四字段与命名哨兵（`ExpectedVersion.ANY/NO_OR_EMPTY_STREAM`）；
- 读侧轻量状态探测思想（streamExists／streamState）。

ddd4j-core 已有的对等资产：

- 读侧抽象 `EventChunkReader／EventChunk`（nextEventNumber 游标循环，07 篇复用同款）；
- 事件契约 `DomainEvent` 携带完整因果元数据（eventId／correlationId／causationId／eventTimestamp，03 篇「已对齐」节）。

## Decision

**自研四方法同步 SPI＋单轨响应式异步扩展**（计划 Task 3.2 定稿）：

- `EventStore` 仅四方法：`append`＋`read`×2（版本区间读）＋`readAll`（按全局 `position`）；
- 配套 `AggregateVersionConflictException`：aggregateType／aggregateId／expectedVersion／actualVersion 四字段，RuntimeException 子类，不写 throws（对齐 ADR-0004 错误模型）；
- `StoredEvent` 携带完整元数据：eventId／correlationId／causationId／eventTimestamp／aggregate 定位／全局 position；`payload` 为类型化 `DomainEvent<?>`（不用 esc-api `CommonEvent` 裸 Object），保留 03 篇要求的 correlation／causation／timestamp 三元追踪字段；
- 异步走独立 `AsyncEventStore`（Reactor Mono／Flux，仅同四方法），阶段 5 Task 5.4 由 `R2dbcEventStore` 实现——**单轨响应式**，不做同步签名的 CompletableFuture 复刻；
- **删除语义＝墓碑领域事件**：走统一追加路径，下游投影自然感知；**不提供存储级 `deleteStream`**（软删／硬删双语义一并不提供，04／05 篇结论）；
- **生命周期不入 SPI**：无 `open()/close()` 与隐式 open，资源管理交各运行时容器（ADR-0003）；
- 流标识不入核心 SPI：以 `(aggregateType, AggregateRootId)` 直接参数对，替代 esc-api `StreamId`（含 KeyValue 参数与投影标记的标识陷阱）；乐观锁不可选关闭——不提供无 expectedVersion 的追加重载；
- 序列化 Jackson 单策略（多态 `@class`），收敛到 Task 3.3 `EventPayloadSerializer`，不引入 `SerializedDataType／EnhancedMimeType／SerializerRegistry` 注册体系。

## Consequences

- 正面：SPI 表面积 4 方法对 esc-api 约 40 签名，实现与审计成本数量级下降；四套持久化实现（JPA／Panache／JDBI／R2DBC）契约一致（ADR-0003）；
- 正面：全局 `position` 使投影位点、断线续传与跨流遍历有统一锚点，补齐 esc-api 缺失的基石能力；
- 正面：墓碑事件让删除进入事件历史，投影／订阅者语义自洽，消除软删版本续接陷阱；
- 正面：单轨异步（Reactor）消除双轨漂移，`R2dbcEventStore` 名实相符；
- 负面：无法直接复用 esc-api 生态的既有实现，外部事件存储需自行桥接（见备选方案）；
- 负面：`@class` 多态序列化把类名写入存储，跨版本重命名事件类需迁移策略（EventPayloadSerializer 内以类型别名缓解，Task 3.3 范围）；
- 义务：阶段 3 `EventStoreContractTest` 三契约用例（追加读回／版本冲突抛异常／readAll 按 position 之后读取）对齐 esc-api 已验证语义（05 篇落地计划）；投影读取统一复用 `EventChunkReader／EventChunk`，不复活推式 ChunkEventHandler。

## Alternatives Considered

- 方案 A：直接集成 esc-api（作为 ddd4j-data-event-store 的底层协议）——**已否决**：双轨全量复制、生命周期入 SPI、无全局 position、StreamId 标识陷阱四项负债（05 篇「缺点」节）均需在其之上再包一层修补，表面积反而大于自研四方法；且 LGPL-3.0 许可与 ADR-0001 冲突。
- 方案 B：以 EventStoreDB／KurrentDB 等商用事件存储为唯一后端（协议原生，不做自研 SPI）——**已否决**：绑定单一外部存储与部署形态，违背 8 运行时×4 持久化矩阵（ADR-0003）；其云协议与商业条款会变成业务方的强制采购项。
- 方案 C：SPI 同时提供软删（`StreamState` 三态）与墓碑两套删除语义——**已否决**：05 篇已证软删无墓碑即下游误读根源，双语义并存徒增实现与文档负担；墓碑领域事件一条路径覆盖合法删除需求。
