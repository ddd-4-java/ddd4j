# ADR-0006: AggregateRoot.apply 反射事件应用机制

## Status

Accepted（2026-08-24）

> 相关：ADR-0002（core 零外部依赖）、ADR-0005（版本校验由 EventStore 乐观锁承接）；实现落地于计划 Task 2.1（注解）／Task 2.2（apply 与回放）／Task 2.3（测试）。

## Context

事件溯源聚合需要两个入口把事件变成状态：命令方法产生新事件（apply），仓储重建时回放历史（loadFromHistory）。fuin 的 AbstractAggregateRoot 用「@ApplyEvent 注解＋反射分发」自动完成（../reference/fuin-api-patterns/01-aggregate-root.md「fuin 的设计」节），聚合子类零 if-else 样板；但其实现有四处硬伤：

- 无方法缓存：每次 apply／回放都 getDeclaredMethods() 全类层次线性扫描，O(事件数×类层次×方法数)（01 篇「缺点」节，MethodExecutor.java:111-129）；
- 忽略历史事件靠聚合级 getIgnoredEvents()：声明为 final 却声称子类可覆写，机制形同虚设（01 篇「缺点」节）；
- 契约校验依赖 objects4j Contract＋jakarta.validation 注解，违背 ADR-0002 零第三方依赖；
- 回放遇到本聚合不认识的事件类型直接抛 IllegalStateException（01 篇 :52），无法跨聚合共享事件流。

ddd4j-core 阶段 1 基线只有 registerEvent() 手动登记（AggregateRoot.java:225-228）：命令方法手动改状态＋手动登记，两处维护易漂移，无自动应用。01 篇「改写」节已给出结论——@ApplyEvent 换自定义 @EventHandler、忽略下沉为方法级 ignoreOnReplay()、反射查找加 ClassValue 缓存（DomainEvent.java:54-59 已有 ClassValue<EventType> 先例）。Task 2.1-2.3 按此落地并经 AggregateRootApplyTest 覆盖（新事件登记／回放不产生未提交事件／ignoreOnReplay 跳过／找不到处理器异常）。

## Decision

**ddd4j-core 的 AggregateRoot 自研注解驱动的反射事件应用，双层 ClassValue 缓存，core 保持零外部依赖（ADR-0002）**：

1. **自定义注解 io.ddd4j.core.ddd.event.EventHandler**：

   - @Retention(RUNTIME)＋@Target(METHOD)（EventHandler.java:33-34）；
   - 匹配规则：单参数且参数类型为 DomainEvent 子类型（AggregateRoot.java:346-347）；
   - ignoreOnReplay() 属性（EventHandler.java:42，默认 false）——方法级忽略，修复 fuin 聚合级 final getIgnoredEvents() 缺陷；
   - 不引入 javax.annotation，core 依赖面不变。

2. **双层 ClassValue 缓存**（AggregateRoot.java:311-328）：

   - AGGREGATE_HANDLER_CACHE（apply 用）与 AGGREGATE_REPLAY_CACHE（回放用），均为 ClassValue<Map<Class<?>,Method>>：聚合类→（事件类型→Method）；
   - 映射由 computeValue 首次访问一次性构建，此后只读复用——每聚合类只做一次反射扫描；
   - scanHandlers（:338-354）自聚合本类沿超类链上遍历（止于 Object），putIfAbsent 保证子类处理器优先于超类同事件类型处理器。

3. **apply(E)**（AggregateRoot.java:257-273）——校验、调用、成功后入队：

   - requireNonNull 校验事件非空（:258）；
   - 按事件运行时类型查 HANDLER_CACHE，找不到处理器抛 IllegalStateException，消息含事件类型与聚合类名（:259-263）；
   - setAccessible(true)＋invoke（:265-266）；ReflectiveOperationException 转 IllegalStateException（:267-270）；
   - 仅在调用成功后加入未提交事件列表（:271）——失败不入队。

4. **loadFromHistory(List)**（AggregateRoot.java:289-308）：

   - 用 REPLAY_CACHE——构建期已剔除 ignoreOnReplay=true 处理器，回放热路径零注解读取；
   - 本聚合未注册的事件类型静默跳过（:296-298）：跨聚合共享事件流成为可能，是对 fuin 抛 ISE 的记录性改进（01 篇 :52）；
   - 回放只重建状态、不产生未提交事件，结束调用 clearDomainEvents()（:307）。

5. **单一 handler 约束**：同一聚合内同一事件类型仅允许一个 @EventHandler 方法。两个同类型 handler 时缓存胜者取决于 getDeclaredMethods() 返回顺序——JVM 规范不保证该顺序，行为不确定；记为设计约束，编译期 lint／启动期校验留作后续项。

6. **版本校验显式推迟**：apply 不做 aggregateVersion 连贯性校验（AggregateRoot.java:236-238 注释明示），乐观锁属阶段 3 EventStore 职责（ADR-0005）。

## Consequences

- 正面：业务方「命令方法 apply(new XxxEvent(...))＋@EventHandler 改状态」即完成事件应用，新增事件类型只加一个处理器方法；
- 正面：ClassValue 缓存规避 fuin 每次 apply／回放的全类层次线性扫描（01 篇「缺点」节），事件多的聚合回放从 O(事件数×类层次×方法数) 降为 O(事件数)；
- 正面：apply 与回放双缓存隔离，ignoreOnReplay 在缓存构建期过滤而非每次调用判断；
- 正面：apply 失败不入队，未提交列表不残留半应用状态；
- 正面：core 仍零外部依赖（ADR-0002 白名单不受影响），EventStore 适配层（ADR-0005）可独立演进；
- 负面：反射调用比直接方法调用慢约 10ns 级（Method.invoke＋setAccessible），高频回放场景可感知；
- 负面：JPMS 命名模块部署需向 ddd4j-core opens 聚合所在包，否则 setAccessible 抛 InaccessibleObjectException（今日 classpath 部署无碍）；
- 负面：私有 handler 可见性依赖 setAccessible（class-path 场景成立），命名模块下受上一条约束；
- 义务：单一 handler 约束的 lint／启动期校验为后续任务（本 ADR 先记录约束）；阶段 3 EventStore 落地乐观锁时补 aggregateVersion 连贯性校验测试（ADR-0005）。

## Alternatives Considered

- 方案 A（**现状备选**）：手写分发——命令方法内 if-else／switch 按事件类型分发，或维持 registerEvent() 手动登记（改状态＋登记两处维护）——**已否决**：样板代码随事件类型线性增长，状态变更与事件登记分离维护必然漂移；fuin 注解分发的核心优点正是消除该样板（01 篇「优点」节）。
- 方案 B：LambdaMetafactory 生成 invokedynamic 直接调用器替代 Method.invoke——**已否决**：性能更优但 JDK17 兼容性需专项测试、实现复杂度高，机制可审计性下降；留作性能优化备选——若基准测试证明反射开销成为瓶颈，经新 ADR 引入。
