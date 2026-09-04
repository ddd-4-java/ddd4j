# ADR-0004: CommandBus 命令总线设计

## Status

Accepted（2026-08-24）

> 相关：ADR-0003（8 运行时适配器继承基线）、ADR-0005（并发冲突异常四字段）；证据见 ../reference/fuin-api-patterns/06-cqrs-command.md。

## Context

命令侧的参照系是 fuin cqrs-4-java 0.6.0（../reference/fuin-api-patterns/06-cqrs-command.md）。其 `CommandExecutor` 契约「有接口、无生态」，四项负债：

- **全生态 0 实现**（06 篇「注意」节）：
  - core 无命令总线／注册中心，quarkus／springboot 模块仅投影侧支撑、无执行器发现机制；
  - 官方示例 ddd-cqrs-4-java-example 的 REST 控制器直调聚合仓储、异常交 JAX-RS ExceptionMapper，完全绕过 CommandExecutor。
- **三参泛型** `<CONTEXT, RESULT, CMD>`（06 篇「缺点」节）：
  - 绝大多数实现把前两参固定为同一对，签名噪音大于收益。
- **EventType 字符串路由**：
  - 组合层按命令自报的 `getEventType()` 字符串查表转发，类与类型串可静默不一致；
  - 根因是 `Command extends Event` 把事件元数据契约压给写侧 DTO。
- **6 个受检异常＋裸类型组合层**：
  - `execute` 声明 5 个聚合异常＋CommandExecutionFailedException 隧道，异常失败与 `Result` 返回双轨并存，每个调用点都要 catch／包装；
  - `AbstractMultiCommandExecutor`＋final `MultiCommandExecutor` 整体 `@SuppressWarnings({"unchecked", "rawtypes"})`，转发链路丢失全部泛型信息。

值得保留的两点先例（06 篇「优点」节）：

- 构造期重复执行器检测——同一 EventType 注册两个执行器即抛异常，冲突在组装期而非首笔命令时暴露；
- 聚合五异常类型化建模——写侧最高频失败（版本冲突／未找到／版本不存在／已删除／已存在）在契约层显式化。

## Decision

**ddd4j-core 既有 `CommandBus`／`DefaultCommandBus` 是命令分发唯一入口**（06 篇已对齐结论 (c)）：

- 路由表为 `Map<Class<? extends Command>, CommandExecutor>`，以 `Class` 为键、编译期可查（06 篇改写 (b)）；
- `DefaultCommandBus` 用 `ConcurrentHashMap`＋`putIfAbsent` 在注册期完成重复执行器检测——保留 fuin「冲突组装期暴露」的优点，去掉其 raw 泛型（DefaultCommandBus.java:14-38）；
- 执行器契约为单参泛型 `<C extends Command>`：
  - `supportedCommands()` 返回 `Set<Class<? extends Command>>`；
  - `execute(C)` 无受检异常，失败统一 `Result.fail(code, message)`，`data()` 返回 `Optional<T>`（06 篇改写 (d)）；
  - 上下文不经显式 ctx 参数，经 `ThreadContext`（TTL 透传）＋`Contexts`（线程优先→全局兜底）解析，命令签名保持纯净（06 篇改写 (e)）；
- **不引入 MultiCommandExecutor 层级**：组合路由能力内置在总线里，业务方不感知「单执行器／组合执行器」差别；
- **阶段 6 各运行时适配器继承 `DefaultCommandBus`** 增量叠加框架能力，不复制路由逻辑：
  - Spring：叠加 `@Transactional`＋自动扫描注册；
  - Quarkus：CDI `Instance<CommandExecutor>` 收集；
  - Micronaut／Helidon：容器事件装配；
  - Javalin／Vert.x／Dropwizard：无容器场景手动注册；
- 错误模型：错误码进 `Result.fail` 编码体系，聚合并发冲突按 05 篇决策以 RuntimeException 落地（不用 throws）。

## Consequences

- 正面：命令入口唯一、路由 Class 键编译期可查，杜绝「类与类型串不一致」的静默错配（06 篇改写 (b)）；
- 正面：无受检异常＋`Optional` 返回，调用方单一错误模型，无双轨判错；
- 正面：7 个运行时适配器共享基类路由实现，新增运行时只写装配差异——与 ADR-0003「投影循环单一实现」同构；
- 正面：保留 fuin 两点先例的价值——注册期冲突检测与聚合异常类型化（经 `Result.fail` 编码体系延续）；
- 负面：`ThreadContext` 隐式上下文使执行链路依赖线程语义，跨响应式运行时（WebFlux／Vert.x）需依赖 TTL 透传的正确配置；
- 负面：总线不内置中间件管道（校验、日志、重试须在执行器内或适配层实现，见备选方案 B）；
- 义务：阶段 6 Task 6.2 的 `@CommandHandler`＋`CommandRegistry` 复用总线落地；7 个运行时适配器（Task 6.4 起）逐个继承 `DefaultCommandBus` 验证装配差异（06 篇落地计划）。

## Alternatives Considered

- 方案 A：fuin 式三参执行器＋MultiCommandExecutor 组合层级——**已否决**：三参泛型签名噪音大、组合层裸类型丢失泛型信息，且 fuin 全生态 0 实现已证明该契约缺乏生态拉力（06 篇「缺点」节）；ddd4j 总线内置路由可达成同一组合能力而无层级税。
- 方案 B：MediatR 风格管道总线（命令处理器＋有序行为管道：校验→日志→事务→重试）——**已否决（本阶段）**：管道抽象会引入行为顺序、短路语义与作用域约定，超出当前「命令→执行器→Result」的领域需求；中间件诉求先在运行时适配层（Spring／Quarkus 拦截器）实现，待真实用例积累后再以新 ADR 评估。
- 方案 C：不设总线，控制器直调聚合仓储（fuin 官方示例路线）——**已否决**：写侧失去统一入口与重复注册检测，聚合异常直接泄漏到接口层；06 篇已指出该示例「完全绕过 CommandExecutor」恰是其生态缺失的表征而非可效仿的设计。
