# Task 2.4 Report: ADR-0006 反射事件应用机制

**Status**: Complete
**Commit**: `48d6bfb3` — `docs(adr): 0006 反射事件应用机制`（feature/2.0.x，单文件 69 行新增，无其他改动）

## 交付物

- `docs/adr/0006-apply-reflection-mechanism.md`（69 行，含末尾空行；落在 60-120 行区间）

## 事实核对（逐条对照源码，均以 file:line 写入 ADR）

| Brief 断言 | 源码核对 |
| --- | --- |
| Task 2.1 注解 RUNTIME/METHOD/ignoreOnReplay | EventHandler.java:33-34（Retention/Target）、:42（ignoreOnReplay 默认 false）✓ |
| apply 校验＋调用＋成功后入队 | AggregateRoot.java:257-273；requireNonNull :258、找不到 handler ISE :259-263、invoke :265-266、失败转 ISE :267-270、入队在 try 之后 :271（失败不入队）✓ |
| loadFromHistory 跳过 ignoreOnReplay＋结束清空 | :289-308；REPLAY_CACHE 构建期剔除 :293/:315/:343、clearDomainEvents :307 ✓ |
| 未注册事件静默跳过（对 fuin ISE 的改进，引 01 篇 :52） | :296-298 `continue`；01-aggregate-root.md:52 为 fuin 抛 ISE 行 ✓ |
| 双 ClassValue 缓存＋scanHandlers 超类遍历 putIfAbsent 子类优先 | :311-328（两个 ClassValue<Map<Class<?>,Method>>）、:338-354（本类→超类，止于 Object；:349 putIfAbsent）✓ |
| 无版本校验（推迟至阶段 3） | :236-238 javadoc 明示 ✓ |
| DomainEvent ClassValue 先例 | DomainEvent.java:54-59（EVENT_TYPES）✓ |
| 测试存在 | AggregateRootApplyTest.java（Task 2.3，commit 4a241ec0）✓ |

## Brief 要求逐项确认

- 五节齐备且顺序正确（Status/Context/Decision/Consequences/Alternatives Considered）；Status: Accepted（2026-08-24）。
- Decision 六项全覆盖：自定义注解（不引 javax）／双层 ClassValue 一次性构建只读复用／apply 校验＋调用＋成功后入队／loadFromHistory 跳过 ignoreOnReplay＋结束清空／**单一 handler 约束（Task 2.3 评审新增，写入 Decision 第 5 条及 Consequences 义务）**／版本校验推迟（引 ADR-0005）。
- Consequences 负面三项齐：反射 ~10ns 级开销／JPMS 需 opens（classpath 部署无碍）／私有 handler 依赖 setAccessible。
- Alternatives：A 手写 if-else（否决：样板）；B LambdaMetafactory/invokedynamic（否决：JDK17 兼容性与复杂度，留性能优化备选）。
- 全角中文标点；无 TBD/TODO；交叉引用 `../reference/fuin-api-patterns/01-aggregate-root.md`、ADR-0002、ADR-0005 均存在。
- 只新增单文件，单 commit。

## 备注

- 「01 篇 :52」经核为 fuin loadFromHistory 抛 ISE 的行号，ADR 引用准确。
- 01 篇「改写」节曾提「异常消息携带 EntityIdPath」；实际落地消息携带事件类型＋聚合类名（AggregateRoot.java:260-263），ADR 按落地事实书写，未照抄计划草稿。
