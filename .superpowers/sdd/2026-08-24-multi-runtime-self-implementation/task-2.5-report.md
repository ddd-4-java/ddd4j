# Task 2.5 Report — ArchUnit 零依赖守护强化 + SLF4J 字节码清零（ADR-0002 迁移义务①）

**Status**: DONE
**Commit**: `2ef42196` — `feat(core): ArchUnit 零依赖守护强化 + SLF4J 字节码清零（ADR-0002 迁移义务①）`（单 commit，6 files changed, +70/−36）
**Branch**: feature/2.0.x（base 48d6bfb3）

## Part A — SLF4J 迁移义务①：6 处引用逐处处置

| # | 文件 | 原引用 | 处置 | 验证 |
|---|------|--------|------|------|
| 1 | `ddd4j-core/src/main/java/io/ddd4j/core/constant/Constants.java:9-10` | `import org.slf4j.Marker / MarkerFactory` | 删除 2 个 import | grep 0 命中 |
| 2 | `Constants.java:53/57/61` | 3 个 `public static Marker accessMarker/authzMarker/bizMarker`（全仓使用 0 处，已复核） | 改为 `public static final String ACCESS_MARKER = "io.hiwepy.access"`（AUTHZ_MARKER/BIZ_MARKER 同理），javadoc 注明 Marker→String 属 2.0.x 破坏性变更（ADR-0002 迁移义务①）；类 javadoc 同步去 SLF4J 表述 | 编译绿 |
| 3 | `ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/DomainEvent.java:12,47` | 死 `@Slf4j`（`log.` 零使用） | 删注解 + import | 编译绿 |
| 4 | `ddd4j-core/src/main/java/io/ddd4j/core/cqrs/query/Query.java:14,67` | 死 `@Slf4j` | 删注解 + import | 编译绿 |
| 5 | `ddd4j-core/src/main/java/io/ddd4j/core/context/ThreadContext.java:7,20` | `@Slf4j` + 3 段真实 trace 日志（get/put/remove 各一） | 删注解 + import + 3 个整段 `if(log.isTraceEnabled())` 块；块外业务逻辑（map 读写、空判清空、返回值）逐字节保留 | diff 复核 + 全测试绿 |
| 6 | `ddd4j-core/src/main/java/io/ddd4j/core/ddd/model/metadata/DomainModelHelper.java:3,44` | `@Slf4j` + 1 段 debug 日志 | 删注解 + import + `if(log.isDebugEnabled())` 整块；双检锁逻辑原样；javadoc「只依赖 slf4j」表述更新为零依赖 | diff 复核 + 全测试绿 |

（ddd4j-kit 的 6 处 @Slf4j 不在本任务范围，未触碰。）

## Part B — ArchUnit 规则（CoreIndependenceTest，沿用现存 @ArchTest 字段风格 + `@AnalyzeClasses(packages = "io.ddd4j.core")` 扫描范围）

新增 4 条（现存 6 条不变，共 10 条）：

1. `no_fuin_reference` — `io.ddd4j.core..` 不依赖 `org.fuin..`（ADR-0001 执行器）。
2. `core_zero_external_dependencies` — 允许清单式（ADR-0002 白名单终态）：`io.ddd4j.., java.., jakarta.., javax.., com.fasterxml.jackson.annotation.., com.fasterxml.jackson.databind.., com.fasterxml.jackson.core.., org.apache.commons.lang3.., com.alibaba.ttl.., io.swagger.v3..`。
3. `no_quarkus_in_core` — 照 `no_spring_in_core` 句式禁 `io.quarkus..`（ADR-0003）。
4. `no_micronaut_in_core` — 禁 `io.micronaut..`（ADR-0003）。

**noSpring 去重**：计划 sketch 的 `noSpringDependencyInCore` 与现存 `no_spring_in_core` 语义重合，未新增；在现存规则 javadoc 补跨引说明（ADR-0002 去重义务）。

**与 brief 清单的唯一偏差——增补 `lombok..`**：首次运行 `core_zero_external_dependencies` 报 247 处违规，逐条核对全部为 `lombok.Generated`（Lombok 对生成构造器/方法自动附加的 CLASS 保留标记，非源码声明依赖，运行时无行为）——与 swagger-annotations 同属编译期豁免类，且现存 `api_package_is_pure_java` javadoc 既有「纯 Java + Jackson + Lombok」表述；已在规则 javadoc 注明该豁免理由。除此 247 处外零违规，真实依赖图（Jackson/lang3/TTL/swagger/家族模块/JDK）全部落白。

## 门禁输出

- `grep -rn "org.slf4j" ddd4j-core/src/main/java/` → **0 命中**（grep exit=1）
- `./mvnw -pl ddd4j-core -am test` → **BUILD SUCCESS，Tests run: 256, Failures: 0, Errors: 0, Skipped: 0**（基线 252 + 新增 4 条 ArchUnit 规则 = 256，CoreIndependenceTest 自身 10/10）

## Self-review

- ThreadContext/DomainModelHelper 仅删日志块，`git diff` 逐行复核业务逻辑零改动。
- Marker 常量改名（accessMarker→ACCESS_MARKER）+ final 化按 brief 指定执行；全仓使用 0 处（改名前已 grep 复核），无连带修改。
- 未做（brief D 节 deferred）：BOM slf4j-api 作用域调整（义务②，跨模块 classpath 卫生）；无过渡期 org.slf4j 允许项（①已清零，无需）。
- 遗留观察：BOM 仍向 core 注入 slf4j-api（compile classpath 可见但字节码零引用），义务②另行任务处理。

## Reply（≤15 行）

Status: DONE
Commit: 2ef42196（feature/2.0.x）
Gate: grep org.slf4j core-main = 0；`./mvnw -pl ddd4j-core -am test` = 256/256 绿（252 基线 + 4 ArchUnit 新规则）
Concerns: 唯一偏差为允许清单增补 `lombok..`（247 处违规全为 lombok.Generated 编译期标记，已在规则 javadoc 注明豁免理由）；Marker 常量改名+final 化属 brief 指定的 2.0.x 破坏性变更
Report: .superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-2.5-report.md
