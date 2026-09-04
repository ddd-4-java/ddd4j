### Task 2.4：写 ADR-0006 反射事件应用机制

**Files:**
- Create: `ddd4j/docs/adr/0006-apply-reflection-mechanism.md`

- [ ] **Step 1: 写 ADR**

按 0001-template 格式写：

```markdown
# ADR-0006: AggregateRoot.apply 反射事件应用机制

## Status
Accepted

## Context
fuin 的 AbstractAggregateRoot.apply 通过反射 + @EventHandler 注解
自动应用事件到聚合状态，规避手写 if-else 分发。
ddd4j-core 当前只有 registerEvent() 手动注册，无自动应用。

## Decision
ddd4j-core 的 AggregateRoot 自研 apply(DomainEvent) 方法：
1. 反射查找本类的 @EventHandler 方法
2. 双层 ClassValue 缓存（聚合类 → 事件类 → Method）
3. apply() 时校验 + 调用 + 加入未提交事件列表
4. loadFromHistory() 跳过 ignoreOnReplay=true 的处理器
5. 用自定义注解 io.ddd4j.core.ddd.event.EventHandler（不引入 javax.annotation）

## Consequences
- 正面：业务方 @EventHandler 注解即可完成事件应用
- 正面：ClassValue 缓存避免每事件反射
- 正面：ddd4j-core 仍零外部依赖
- 负面：反射调用比直接方法调用慢 ~10ns

## Alternatives Considered
- 方案 A：业务方手写 apply(if-else)——已否决（重复样板代码）
- 方案 B：LambdaMetafactory 生成 invokedynamic——性能更好但 JDK 17 兼容性需测试
```

- [ ] **Step 2: 提交**

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add docs/adr/0006-apply-reflection-mechanism.md
git commit -m "docs(adr): 0006 反射事件应用机制"
```

---


---

## Controller context

1. **已落地事实（写 ADR 前逐条核对源码）**：Task 2.1 `EventHandler` 注解（RUNTIME/METHOD/ignoreOnReplay）；Task 2.2 `apply`/`loadFromHistory` + 双 `ClassValue<Map<Class<?>,Method>>` 缓存 + scanHandlers 超类遍历 putIfAbsent 子类优先；apply 失败不入队；回放结束 clearDomainEvents；回放静默跳过未注册事件（对 fuin 抛 ISE 的记录性改进，引 01 篇 :52）；无版本校验（显式推迟至阶段 3 EventStore 乐观锁）。
2. **新增已知约束（Task 2.3 评审发现，必须写入）**：同一聚合内**同一事件类型仅允许一个 @EventHandler**——两个同类型 handler 的 apply 缓存胜者依 getDeclaredMethods 未定义顺序而定（JVM 规范不保证），行为不确定。ADR 记录为设计约束（可留 lint/未来校验为后续项）。
3. 结构与文风：遵循 `docs/adr/0000-template.md` 五节（Status/Context/Decision/Consequences/Alternatives Considered）；全角中文标点；60-120 行；H1 `# ADR-0006: AggregateRoot.apply 反射事件应用机制`；引用 `../reference/fuin-api-patterns/01-aggregate-root.md` 与 ADR-0002 的先例行文。Status: Accepted。
4. Decision 至少覆盖：自定义注解（不引 javax/JSpecify）｜双层 ClassValue（聚合类→事件类型→Method，一次性构建只读发布）｜apply 校验+调用+成功后入队｜loadFromHistory 跳过 ignoreOnReplay+结束清空｜单 handler 约束｜版本校验推迟。
5. Consequences 负面须含：反射调用开销（~10ns 级）｜JPMS 命名模块需 opens（今日 classpath 部署无碍）｜私有 handler 依赖 setAccessible（class-path 场景）。
6. Alternatives：手写 if-else 分发（否决：样板）；LambdaMetafactory/invokedynamic（否决：JDK17 兼容性与复杂度，留性能优化备选）。
7. 只新增 `docs/adr/0006-apply-reflection-mechanism.md` 单文件；不动其他。单 commit：`docs(adr): 0006 反射事件应用机制`。

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-2.4-report.md`。Reply ≤15 lines.
