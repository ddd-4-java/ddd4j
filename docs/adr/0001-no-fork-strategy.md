# ADR-0001: 不 fork fuin，reference-only 策略

## Status

Accepted（2026-08-24）

> 相关：ADR-0002（core 零依赖）、ADR-0003（跨 8 运行时）、ADR-0004（CommandBus）、ADR-0005（EventStore SPI）；ADR-0006（反射事件应用，阶段 2 Task 2.4）。

## Context

ddd4j 2.0.x 的事件溯源／CQRS 能力建设以 fuin 生态为最接近的参照系。该生态相关构件均为 **LGPL-3.0** 许可：

- ddd-4-java 0.7.0——聚合根、领域事件、EntityIdPath
  （../reference/fuin-api-patterns/01-aggregate-root.md、03-domain-event.md）；
- cqrs-4-java 0.6.0——命令侧契约与投影读侧
  （../reference/fuin-api-patterns/06-cqrs-command.md、07-cqrs-projection.md）；
- ddd-cqrs-4-java-example 0.5.0——官方示例
  （REST 控制器直调聚合仓储，见 06 篇「注意」节）；
- esc-api 0.9.0——外部构件，EventStore SPI 所在
  （../reference/fuin-api-patterns/05-event-store.md）。

LGPL-3.0 对「修改并再分发」的要求包括三项：LICENSE 文件必须随分发物跟随、原版权声明必须保留、修改处必须显著标注。fork 后改名集成即落入此范围，ddd4j 将失去「纯 Apache-2.0 单许可证」这一对业务方最关键的交付属性。

更进一步，fork 并不能消除 fuin 的设计负债，只是把负债过户到 ddd4j 名下：

- `AbstractAggregateRoot`（../reference/fuin-api-patterns/01-aggregate-root.md「缺点」节）：
  - `getIgnoredEvents()` 声明为 `protected final`，javadoc 却声称「Subclasses can overwrite」——忽略废弃历史事件的机制形同虚设；
  - `MethodExecutor.same()` 用 `expected[0] != actual[i]` 把首参类型与每个实参比较，多参匹配结果是错的；
  - 反射查找无任何 Method 缓存，回放是 O(事件数 × 类层次 × 方法数)。
- esc-api（../reference/fuin-api-patterns/05-event-store.md「缺点」节）：
  - 同步／异步双轨全量复制，约 20 个签名×2；
  - `deleteStream` 软删不发墓碑事件，下游无从感知删除；
  - EJB 时代遗留（ejb-jar.xml 配置异常的 CAUTION）与「隐式 open」生命周期约定。
- cqrs-4-java（../reference/fuin-api-patterns/06-cqrs-command.md）：
  - CommandExecutor「有接口、无生态」——core 无命令总线，全生态 0 个执行器实现，官方示例完全绕过该契约。
- 投影读侧（../reference/fuin-api-patterns/07-cqrs-projection.md「缺点」节）：
  - ViewManager 写死 Spring，Quarkus 需整份重写同一投影循环。

## Decision

**不 fork fuin。** 采用「高精度参考 + 完全自研」：

- ddd4j 全部自有代码保持 **Apache-2.0**，零 LGPL 代码引入；
- fuin 仅作为 **API 形态参考**，以 reference-only 文档沉淀于 `docs/reference/fuin-api-patterns/`；
- 参考系列三原则：只读不写（reference-only）、不复用代码（no code reuse）、不集成进 monorepo；
- 参考系列 8 篇（01-08）已全部完成并经本地快照 **file:line 逐条核验**，构成 reference-only 策略的落地证据：每篇给出「借鉴／改写（或已对齐）／不借鉴」三分结论，阶段 2-7 各自研任务逐篇回链。

| 篇 | 主题 | 对自研的核心输入 |
|---|---|---|
| 01-aggregate-root | 反射事件应用 | 借鉴双通道分离；修 final／缓存缺陷 |
| 02-entity-id-path | 子实体路由 | 补 validate |
| 03-domain-event | 事件契约 | ddd4j 已对齐并在分发侧超出 |
| 04-event-sourcing-repository | ES 仓储 | 墓碑事件结论 |
| 05-event-store | 事件存储 SPI | 四方法 SPI 决策（ADR-0005） |
| 06-cqrs-command | 命令侧 | 总线内置＋Class 路由（ADR-0004） |
| 07-cqrs-projection | 投影读侧 | 框架无关＋ViewScheduler SPI（ADR-0003） |
| 08-architecture-test | ArchUnit 守护 | 允许清单＋ban 互补（ADR-0002） |

## Consequences

- 正面：ddd4j 全量 Apache-2.0，业务方引入无许可证审查负担，可安全用于闭源商业分发；
- 正面：API 由 ddd4j 100% 自控，可按 2.0.x 兼容承诺自由演进签名，不受上游发版节奏约束；
- 正面：跨 8 运行时（ADR-0003）自研不受 fuin 的 **Spring 5＋Java 8 锁定**——其基线停留在旧框架栈，fork 即继承该基线并被迫与其兼容矩阵绑定；
- 正面：不受 fuin 的框架绑定拖累——其投影调度写死 Spring、事务细节泄漏进投影循环（07 篇），fork 后仍需逐条改造；
- 负面：自研工作量大，全计划合计 56-84 天（阶段 2-9 预估）；
- 负面：失去 fuin 现成的多序列化（jackson／jaxb／jsonb 三模块各一份，03 篇指出实为重复拷贝）与框架适配（springboot／quarkus），需按 ADR-0003 自建 8 运行时适配、按 ADR-0005 收敛为 Jackson 单策略序列化。

## Alternatives Considered

- 方案 A：fork + 改名集成——**已否决**：
  - LGPL-3.0 跟随使 ddd4j 失去单许可证属性；
  - fuin 缺陷（01／05 篇所列）在 fork 路线下仍需逐一修复，总成本接近自研而法律属性永久劣化。
- 方案 B：fuin 作为可选 `ddd4j-data-fuin` 适配模块引入（依赖注入、按需装配）——**已否决**：
  - 模块本身及其传递依赖仍是 LGPL，随 ddd4j 官方物料分发即触发许可证跟随；
  - fuin 缺命令总线与 EventStore 全局 position（05／06 篇），适配层补不齐语义缺口；
  - 维护两套 API 心智的成本高于自研。
