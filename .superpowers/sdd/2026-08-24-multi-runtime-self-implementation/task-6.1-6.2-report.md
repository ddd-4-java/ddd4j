# Task 6.1+6.2 Report — ddd4j-data-cqrs（@CommandHandler + CommandRegistry）

- **Commit**: `9621c5ae` `feat(data): ddd4j-data-cqrs——@CommandHandler 注解 + CommandRegistry（含 core 幽灵 javadoc 修复）`（单 commit，8 files，+508/-1）
- **Gate**: `./mvnw -pl ddd4j-data/ddd4j-data-cqrs,ddd4j-core -am install` → **BUILD SUCCESS**
  - ddd4j-data-cqrs：**12 tests**（≥10 ✓）＝ CommandHandlerTest 2 + CommandRegistryTest 7 + CqrsModuleIndependenceTest 3（@ArchTest 规则）
  - ddd4j-core：**261 tests**，0 failures（保持，javadoc 改动零测试影响 ✓）
  - Reactor：ddd4j / ddd4j-dependencies / ddd4j-annotation / ddd4j-kit / ddd4j-core / ddd4j-data / ddd4j-data-cqrs 全 SUCCESS

## 交付物

| 文件 | 说明 |
| --- | --- |
| `ddd4j-data/ddd4j-data-cqrs/pom.xml` | parent ddd4j-data；依赖仅 ddd4j-core + ddd4j-annotation（${revision}）；测试依赖走父级全局块（junit/assertj/archunit/mockito），零框架依赖 |
| `.../src/main/java/io/ddd4j/data/cqrs/CommandHandler.java` | `@Documented @Retention(RUNTIME) @Target(TYPE)`；`Class<? extends Command> value()`；javadoc 引 ADR-0004、6.3+ 适配器扫描装配 |
| `.../src/main/java/io/ddd4j/data/cqrs/CommandRegistry.java` | `register`（requireNonNull + 先校验全部类型再落库）；`executors()`（不可变去重视图）；`findExecutor`（未注册返回 null） |
| `.../src/test/java/io/ddd4j/data/cqrs/CommandHandlerTest.java` | ①元注解守护（@Target={TYPE} + @Retention=RUNTIME，照 EventHandlerTest 模式）②value 运行时反射可读 |
| `.../src/test/java/io/ddd4j/data/cqrs/CommandRegistryTest.java` | 7 测试覆盖 brief ①-⑤全部场景 + NPE + 多执行器/多类型路由 |
| `.../src/test/java/io/ddd4j/data/cqrs/arch/CqrsModuleIndependenceTest.java` | `no_spring_in_cqrs_module` / `no_quarkus_in_cqrs_module` / `cqrs_deps_allowlist`（io.ddd4j../java../lombok..） |

## Brief correction record（相对计划 sketch 的语义修正，均已入 javadoc）

1. **零框架**：sketch 曾把 SpringCommandBus 放本模块——未采纳（Spring 适配归 6.3）。
2. **register 整批拒绝（all-or-nothing）**：sketch 的 `Map.put` 静默覆盖 → 改为先校验 `supportedCommands()` 全部类型无冲突再统一落库；任一冲突抛 `IllegalStateException`（消息含命令类型 FQN）且该执行器全部类型不落库。比 DefaultCommandBus 逐个 putIfAbsent 更严谨（理由：registry 是长生命周期共享组件，须避免半注册状态；bus 是一次性组装对象），差异理由已写入 CommandRegistry javadoc。
3. **pom 偏离 sketch**：sketch 的 pom 含 ddd4j-data-event-store 依赖与 junit 声明——按 brief 剔除（仅 core+annotation，测试依赖走父级全局块）。
4. **root pom 注册未做**：计划 6.1 Step1 写「ddd4j/pom.xml 加 module」——本仓库 ddd4j-data 子模块（event-store 等）均不单独列 root pom（聚合器模式），brief 亦只要求 ddd4j-data/pom.xml；root pom 未动。
5. **ArchUnit 测试命名**：brief 写 `EventStoreCqrsModuleIndependenceTest`——"EventStore" 前缀系 event-store 模板复制残留（本模块与事件存储无关），命名改为 `CqrsModuleIndependenceTest`（沿用 `<模块>ModuleIndependenceTest` 惯例）。包名 `io.ddd4j.data.cqrs.arch` 与 3 条规则名均照 brief。
6. **jakarta 未入允许清单**：brief「jakarta..（若用）」——本模块未用 jakarta，故不入清单（保持最小依赖面，javadoc 注明新增依赖须 ADR 修订）。

## 中断残留续用 + 运行期发现并修复的缺陷

- 派发时已存在（上一中断派发遗留、未提交）：模块 pom + CommandHandler.java + CommandRegistry.java，与 brief 核对无误后**原样续用**（仅下述一处修复），测试三件为本任务新写。
- **executors() 去重修复**（测试驱动的真实缺陷）：原实现返回 `Collections.unmodifiableCollection(map.values())`——映射按命令类型键控，多类型执行器会出现 N 次，与自身 javadoc「多类型执行器只出现一次」矛盾，且向 `new DefaultCommandBus(registry.executors())` 喂入重复实例。改为 `unmodifiableCollection(new LinkedHashSet<>(values()))`，javadoc 补记去重理由；由 `multipleExecutorsRouteToTheirOwnCommandTypes` 断言 `containsOnlyOnce` 守护。

## Fold-in diff（E-item：core 幽灵 javadoc 修复，同 commit）

`ddd4j-core/src/main/java/io/ddd4j/core/cqrs/command/CommandExecutor.java:20`

```diff
- * {@code io.ddd4j.core.cqrs.command.CommandRegistry}：
+ * {@code io.ddd4j.data.cqrs.CommandRegistry}：
```

（兑现 Task 1.7 deferred 项；文件内另一处无包名前缀的 `CommandRegistry` 注释无需改。）

## 聚合器 pom 确认

`ddd4j-data/pom.xml` 仅含上一派发预置的一行 `<module>ddd4j-data-cqrs</module>`（置于 `<modules>` 首位，cqrs < crypto 字母序成立）——本任务**未改动该文件**，仅原样纳入 commit 使注册生效。

## Self-review

- 命令契约零重定义：模块仅引用 core 的 Command/CommandExecutor；路由/重复检测语义与 DefaultCommandBus/ADR-0004 一致。
- 残留文件核验：CommandHandler 的 `@Documented`、value 路由以 `supportedCommands()` 为准的说明等均与 brief 相符。
- 未提交无关文件：`docs/superpowers/plans/*.md`（两份计划文档）仍为 untracked，未纳入本 commit。
