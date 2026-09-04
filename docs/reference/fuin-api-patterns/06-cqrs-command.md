# 06. fuin API 模式：CQRS 命令侧

> 对应 README 索引第 06 项；本篇为「已对齐」主题——ddd4j-core 现有 Command／CommandExecutor／CommandBus／Result 四件套已与 fuin 对齐，并在类型安全、总线内置、错误模型三处超出；重在盘点存量与微调，不新增借鉴。

## 来源

- 仓库：https://github.com/fuinorg/cqrs-4-java
- 版本：0.6.0（本地快照：`workspace-ddd4j-boot/cqrs-4-java`，tag `0.6.0`）
- 文件（相对 `core/src/main/java/org/fuin/cqrs4j/core/`）：
  - `Command.java:25-27`（命令接口，继承 ddd-4-java 的 `Event`，非纯 marker）
  - `CommandExecutor.java:37-64`（三参泛型执行器契约）、`CommandExecutionFailedException.java:25-27`（受检隧道异常）
  - `AbstractMultiCommandExecutor.java:45-104`（组合执行器：EventType→executor 路由表）、`MultiCommandExecutor.java:33-55`（final 子类）
  - `Result.java:31-63`（结果接口）、`ResultType.java:23-33`（OK/WARNING/ERROR 三态枚举）
  - `AggregateCommand.java:33-43`（聚合定向命令）、`UrlParamEntityIdPathNotEqualsCmdException.java:13-30`（URL 与命令体实体路径一致性校验）
- 关键 API：`getCommandTypes()` 返回 `Set<EventType>`；`execute(ctx, cmd)` 声明 6 个受检异常（5 个聚合异常＋CommandExecutionFailedException，:61-62）；`Result<DATA>.getData()` 返回 `@Nullable DATA`
- 注意（Task 1.7 核实）：fuin **core 无命令总线**——无 CommandBus／注册中心；且 quarkus／springboot 模块仅投影侧支撑（ViewManager 等），无执行器发现机制；官方示例 ddd-cqrs-4-java-example 0.5.0（本地快照 `workspace-ddd4j-boot/ddd-cqrs-4-java-example`）的 REST 控制器直调聚合仓储、异常交 JAX-RS ExceptionMapper，**完全绕过 CommandExecutor**

## fuin 的设计

命令侧四件套：Command（继承 Event，天然携带 eventId／eventType／correlationId 等元数据）→ CommandExecutor（三参泛型＋EventType 路由）→ MultiCommandExecutor（组合多执行器）→ Result（type／code／message／data 四元结果）。

**1）执行器契约——CommandExecutor（CommandExecutor.java:37-62）**

```java
public interface CommandExecutor<CONTEXT, RESULT, CMD extends Command> {

    @NotNull
    Set<EventType> getCommandTypes();

    RESULT execute(@NotNull CONTEXT ctx, @NotNull CMD cmd) throws AggregateVersionConflictException, AggregateNotFoundException,
            AggregateVersionNotFoundException, AggregateDeletedException, AggregateAlreadyExistsException, CommandExecutionFailedException;
}
```

**2）组合路由——AbstractMultiCommandExecutor（AbstractMultiCommandExecutor.java:47、:78-82、:93-101）**

构造期遍历各执行器 `getCommandTypes()` 填 `Map<EventType, CommandExecutor>`，同一 EventType 注册两个执行器即抛 ConstraintViolationException（:78-81）；`execute` 按 `cmd.getEventType()` 查表转发（:97），未命中抛 IllegalArgumentException（:99）。

**3）结果契约——Result／ResultType（Result.java:31-63、ResultType.java:23-33）**

`getType()`（OK/WARNING/ERROR）＋`getCode()`＋`getMessage()`＋`getData()`（`@Nullable DATA`，非 Optional）；成功判定靠调用方比较 `getType() == ResultType.OK`。

## 优点（值得借鉴的）

- 聚合五异常类型化建模（CommandExecutor.java:54-59）：写侧最高频失败（版本冲突／未找到／版本不存在／已删除／已存在）在契约层显式化，调用方无法视而不见。
- 构造期重复执行器检测（AbstractMultiCommandExecutor.java:78-82）：路由冲突在组装期而非首笔命令时暴露。
- `AggregateCommand` 聚合定向契约（AggregateCommand.java:33-41）＋URL 与命令体实体路径一致性校验（UrlParamEntityIdPathNotEqualsCmdException.java:13-30）：防「路径 A 改实体 B」的 REST 写侧错位。
- Result 四元结构 type/code/message/data（Result.java:31-63）：与 HTTP 响应语义一一对应，可直接映射到接口层响应。

## 缺点（应规避的）

- **execute 抛 6 个受检异常**（CommandExecutor.java:61-62）＋CommandExecutionFailedException 受检隧道包装其余受检异常（CommandExecutionFailedException.java:25-27）：每个调用点都要 catch／包装，样板沉重；且异常失败与 Result 返回双轨并存，消费方两头判。
- 三参泛型 `<CONTEXT, RESULT, CMD>`（CommandExecutor.java:33-37）：绝大多数实现把 CONTEXT/RESULT 固定为同一对，签名噪音大于收益。
- 组合层整体裸类型：AbstractMultiCommandExecutor 标 `@SuppressWarnings({"unchecked", "rawtypes"})`（:44）持 `Map<EventType, CommandExecutor>`（:47），MultiCommandExecutor 构造器收 raw `CommandExecutor`（MultiCommandExecutor.java:32、:41），转发链路丢失全部泛型信息。
- EventType 字符串路由（AbstractMultiCommandExecutor.java:97）：路由键是命令自报的 `getEventType()` 字符串而非 Class，类与类型串可静默不一致；根因是 `Command extends Event`（Command.java:25）把事件元数据契约压给写侧 DTO。
- core 无总线／注册中心且全生态未接线：quarkus/springboot 模块只有投影侧支撑，官方示例 REST 控制器直调聚合（PersonResource.java:53-73 即抛聚合异常）——CommandExecutor 契约「有接口、无生态」，纯 Java 场景零支撑。
- `getData()` 返回 `@Nullable DATA`（Result.java:62-63）且无 isSuccess() 便捷方法：消费侧判空＋枚举比较样板。

## ddd4j 自研决策

> **结论：ddd4j-core 现有命令侧四件套已对齐 fuin 契约，并在类型安全、总线内置、错误模型三处超出；本篇零新增借鉴。**

- **借鉴（新增）**：无。
- **已对齐（对等）**：
  - Command 概念：ddd4j 纯 marker＋四条设计原则 javadoc（Command.java:8-14、:46）↔ fuin Command.java:25（fuin 侧非纯 marker，差异归入改写 (a)）；
  - CommandExecutor 概念：「声明支持集＋单一 execute」骨架（CommandExecutor.java:48-63 ↔ fuin CommandExecutor.java:37-62）；
  - Result 概念：code／message／data＋成功判定（Result.java:21-77 ↔ fuin Result.java:31-63）。
- **改写／超出（逐条核实）**：
  - (a) 泛型收窄：`<C extends Command>`（CommandExecutor.java:48）vs fuin 三参 `<CONTEXT, RESULT, CMD>`（fuin CommandExecutor.java:33-37）；Command 收窄为纯 marker（Command.java:46）vs fuin `Command extends Event` 强制命令携带事件元数据（fuin Command.java:25）；
  - (b) `supportedCommands()` 返回 `Set<Class<? extends Command>>`（CommandExecutor.java:55）——Class 路由编译期可查，vs fuin `Set<EventType>` 字符串值对象（fuin CommandExecutor.java:44-45）；
  - (c) 总线内置：`CommandBus`（CommandBus.java:6-9）＋`DefaultCommandBus`（DefaultCommandBus.java:14-38，`ConcurrentHashMap`＋`putIfAbsent` 重复执行器检测 :33-36）——fuin core 无总线，适配层与官方示例亦未接线（控制器直调聚合）；
  - (d) 错误模型：`execute(C)` 无受检异常、失败统一 `Result.fail(code, message)`（CommandExecutor.java:63、Result.java:60-69），`data()` 返回 `Optional<T>`（Result.java:75-77）——vs fuin 抛 5 个聚合受检异常＋CommandExecutionFailedException（fuin CommandExecutor.java:61-62）、`getData()` 裸 `@Nullable`（fuin Result.java:62-63）；聚合并发冲突的类型化异常按计划 Task 3.2 以 RuntimeException 落地（05 篇决策，不用 throws）；
  - (e) 上下文：fuin 显式 `CONTEXT ctx` 参数注入（fuin CommandExecutor.java:61）；ddd4j execute 无 ctx，经 `ThreadContext`（TransmittableThreadLocal，线程池可透传，ThreadContext.java:23-37）＋`Contexts`（线程优先→全局兜底，Contexts.java:46-52）解析，命令签名保持纯净。
- **不借鉴**：
  - MultiCommandExecutor 抽象类层级（Abstract＋final 双层＋raw 泛型）——ddd4j 用 CommandBus 单一入口，路由表内置；
  - EventType 字符串路由——Class 路由已替代；
  - `Command extends Event` 元数据耦合——命令是意图载体而非事件，纯 marker 解耦（Command.java:46）。

## 落地计划

- [ ] 阶段 6（Task 6.2）：ddd4j-data-cqrs 的 `@CommandHandler` 注解＋`CommandRegistry` 复用 ddd4j-core 既有 `CommandBus`/`CommandExecutor`，不另起 fuin 式三参契约。
- [ ] 阶段 6：spring／quarkus／micronaut／helidon／javalin／vertx／dropwizard 7 个运行时适配器统一继承 `DefaultCommandBus`（如 SpringCommandBus 叠加 `@Transactional`＋自动扫描）。
- [ ] 阶段 3（Task 3.2）：聚合并发冲突走 RuntimeException（不做受检 throws），错误码进 `Result.fail` 编码体系。
- [ ] Task 1.10：ADR-0004（0004-command-bus-design）引用本文档「总线内置＋Class 路由＋无受检异常」三项超出结论。
