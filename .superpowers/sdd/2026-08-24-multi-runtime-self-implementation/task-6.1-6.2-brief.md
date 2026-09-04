# Task 6.1+6.2 Brief（合并派发）— ddd4j-data-cqrs：框架无关 CQRS SPI 模块

## 背景与已核事实
- ddd4j-core 已有完整命令侧契约（Task 1.7 已对齐确认）：`Command`（marker）、`CommandExecutor<C>`（supportedCommands→Set<Class>, execute→Result）、`CommandBus`、`DefaultCommandBus`（**putIfAbsent + 重复抛 ISE**——重复检测是 ADR-0004 记录的构建期强项）。**本模块不重定义任何命令契约**，只补两件：发现注解 + 注册中心。
- 计划 6.1/6.2 sketch 曾把 SpringCommandBus 放进本模块——**错误**（Spring 适配在 6.3）；本模块**零框架**。
- Task 1.7 deferred 项随本任务兑现：core `CommandExecutor.java:20` javadoc 引用不存在的 `io.ddd4j.core.cqrs.command.CommandRegistry` → 本模块落地真实 `io.ddd4j.data.cqrs.CommandRegistry` 后**回改该 javadoc**（一并入 commit）。

## 交付
### A. 模块 `ddd4j-data/ddd4j-data-cqrs`
pom：parent ddd4j-data；依赖 `ddd4j-core` + `ddd4j-annotation`（均 ${revision}）；测试依赖走父级全局块（junit/assertj/mockito/archunit——勿加 Spring）。注册于 ddd4j-data/pom.xml 字母序**首位之前**（cqrs < crypto，插在 `<modules>` 第一个 `<module>ddd4j-data-crypto</module>` 之前）。

### B. `@CommandHandler`（io.ddd4j.data.cqrs）
照计划 sketch：`@Retention(RUNTIME) @Target(TYPE)`，`Class<? extends Command> value()`（Command = io.ddd4j.core.cqrs.command.Command）。javadoc：标注于 CommandExecutor 实现类，各运行时适配器（6.3+ spring/quarkus/...）扫描此注解装配；引 ADR-0004。

### C. `CommandRegistry`（同包）
照 sketch + **一处语义修正（记 brief correction）**：sketch 的 `Map.put` 静默覆盖与 DefaultCommandBus 的 putIfAbsent+抛 ISE 矛盾——改为**register 对同命令类型的重复注册抛 `IllegalStateException`（消息含命令类型名）**，与 DefaultCommandBus/ADR-0004 语义一致。API：`void register(CommandExecutor<?> executor)`（requireNonNull；遍历 supportedCommands 逐类型 putIfAbsent，重复即抛——注意抛出前已注册的部分**保留还是回滚**？选**抛出即整批拒绝**：先校验全部类型无冲突再落 map，避免半注册——比 DefaultCommandBus 的逐个 putIfAbsent 更严谨，javadoc 注明差异理由）；`Collection<CommandExecutor<?>> executors()`（不可变视图）；`<C extends Command> CommandExecutor<C> findExecutor(Class<C> commandType)`（未注册返回 null——bus 层负责抛）。javadoc：供 6.3+ 适配器收集后喂给 `new DefaultCommandBus(registry.executors())`。

### D. 测试
- `CommandHandlerTest`：注解 RUNTIME 可反射读取、TYPE 目标、value 可取（照 EventHandlerTest 第 3 用例模式——守护 Retention/Target 元注解）。
- `CommandRegistryTest` ≥5：①注册后 findExecutor 命中且类型正确；②未注册类型返回 null；③同类型重复注册抛 ISE 且消息含命令类名；④**整批拒绝语义**——executor 声明 {A,B}，A 已被他人注册 → 抛 ISE 且 **B 也未落库**（findExecutor(B)==null）；⑤executors() 不可变（抛 UnsupportedOperationException）+ 多 executor 多类型路由。
- 模块 ArchUnit `EventStoreCqrsModuleIndependenceTest`（包 io.ddd4j.data.cqrs.arch）≥3：`cqrs_deps_allowlist`（io.ddd4j.data.cqrs.. onlyDependOn io.ddd4j../java../jakarta..(若用)/lombok..）；`no_spring_in_cqrs_module`；`no_quarkus_in_cqrs_module`。

### E. 兑现 deferred：core javadoc 回改
`ddd4j-core/src/main/java/io/ddd4j/core/cqrs/command/CommandExecutor.java:20` 的 `{@code io.ddd4j.core.cqrs.command.CommandRegistry}` 改为 `{@code io.ddd4j.data.cqrs.CommandRegistry}`（Edit 工具，仅此一行措辞）。

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-cqrs,ddd4j-core -am install` BUILD SUCCESS；报告双模块计数（cqrs 模块 ≥2+5+3=10 测试；core 261 保持——javadoc 改动零测试影响）。

## 提交
单 commit：`feat(data): ddd4j-data-cqrs——@CommandHandler 注解 + CommandRegistry（含 core 幽灵 javadoc 修复）`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-6.1-6.2-report.md`。Reply ≤15 lines.
