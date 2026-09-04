# Task 2.1 Report: @EventHandler 注解（ddd4j-core）

- Status: **DONE** — commit `490d41d9` on `feature/2.0.x`（父提交 36809966）
- Files:
  - Create: `ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/EventHandler.java`
  - Test: `ddd4j-core/src/test/java/io/ddd4j/core/ddd/event/EventHandlerTest.java`

## TDD Evidence

### RED（test-compile 编译失败 = 新类的 RED）

Command: `./mvnw -pl ddd4j-core -am test-compile`（`-am` 必需：rebase 后 revision 升为
2.0.x.20260730-SNAPSHOT，本地仓库无同版本 ddd4j-annotation/ddd4j-kit 快照，单 `-pl` 无法解析依赖）

```
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.15.0:testCompile (default-testCompile) on project ddd4j-core: Compilation failure: Compilation failure:
[ERROR] /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/test/java/io/ddd4j/core/ddd/event/EventHandlerTest.java:[52,10] 找不到符号
[ERROR]   符号:   类 EventHandler
[ERROR]   位置: 类 io.ddd4j.core.ddd.event.EventHandlerTest.SampleHandler
[ERROR] /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/test/java/io/ddd4j/core/ddd/event/EventHandlerTest.java:[56,10] 找不到符号
[ERROR]   符号:   类 EventHandler
[ERROR]   位置: 类 io.ddd4j.core.ddd.event.EventHandlerTest.SampleHandler
[INFO] BUILD FAILURE
```

### GREEN

Command: `./mvnw -pl ddd4j-core -am test -Dtest=EventHandlerTest -Dsurefire.failIfNoSpecifiedTests=false`

```
[INFO] Running io.ddd4j.core.ddd.event.EventHandlerTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.017 s -- in io.ddd4j.core.ddd.event.EventHandlerTest
[INFO] Results:
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Full suite guard

Command: `./mvnw -pl ddd4j-core -am test`

- ddd4j-core 自身：**240 tests / 42 classes**（原 237 + 本任务新增 3），除下述 1 个预先存在的环境性失败外全部通过；ArchUnit CoreIndependenceTest 通过（注解仅依赖 `java.lang.annotation`，零新依赖）。
- **预先存在的失败（与本任务无关，已用干净 HEAD 复现证明）**：`io.ddd4j.core.ddd.event.DomainEventJsonTest.shouldSerializeEventMetadataAsStableScalarValues` 报
  `InvalidDefinitionException: Java 8 date/time type java.time.ZonedDateTime not supported by default: add Module "com.fasterxml.jackson.datatype:jackson-datatype-jsr310"`。
  - 证明方法：将本任务两个新文件移出后，在 HEAD 36809966 上单独运行该测试，报同样的错误；随后恢复文件。
  - 根因：commit 939eaa6d 修复该测试时依赖「jsr310 已在 test classpath（继承自当时本地安装的 ddd4j-kit:2.0.x.20260630-SNAPSHOT 旧 pom，其中仍声明 jackson-datatype-jsr310）」。rebase 把 revision 升为 20260730 后旧安装 pom 不再匹配；`-am` 反应堆用当前源码的 ddd4j-kit pom（Jackson 2→3→2 迁移期间 2193145e 删掉了 jsr310 声明，注释还停留在「J3 内置」），而 Jackson 2 的 databind 并不内置 jsr310。整个仓库任何 pom 均未声明 jsr310。
  - 建议修复（超出本任务边界，本任务 brief 明令不改 ddd4j-core/pom.xml）：给 ddd4j-core（或恢复 ddd4j-kit）声明 `jackson-datatype-jsr310` test/compile 依赖。建议由控制器开独立任务处理。

## EntityId surface note（计划草图 → 真实接口的适配）

计划草图中的 `record OrderId(String value) implements EntityId` 只实现了 `asString()`。真实接口
`io.ddd4j.core.ddd.event.EntityId`（extends Serializable）有三个方法：

- `EntityType getType()`
- `String asString()`
- `String asTypedString()`

测试中的 `OrderId` record 按真实接口补齐了 `getType()`（复用 `StringEntityType("Order")` 常量）与
`asTypedString()`（`TYPE.asString() + ":" + value`，与 `StringEntityId` 同构）。`OrderCreatedEvent`
构造走 `super(new EntityIdPath(new OrderId("order-1")))`，完全贴合 `DomainEvent<ID extends EntityId>`
的 protected 构造器签名。

## 实现说明

- `EventHandler.java`：`@Retention(RetentionPolicy.RUNTIME)` + `@Target(ElementType.METHOD)` +
  `boolean ignoreOnReplay() default false`，javadoc 按计划原文（含 `{@link AggregateRoot#apply(DomainEvent)}`
  前向引用——已确认父 pom `disable-javadoc-doclint` profile 生效（`-Xdoclint:none`），且 javadoc 插件
  配置为不因待收敛链接阻断发布附件，Task 2.2 落地 apply 前 build 不受影响）；相比草图仅补了
  `ignoreOnReplay()` 的 `@return` 标签，与代码库 javadoc 风格一致。
- 测试 3 个用例：① 运行时可见 + `ignoreOnReplay` 默认 false；② `ignoreOnReplay = true` 运行时可读；
  ③ 声明层面断言 `@Target` 仅 METHOD、`@Retention` 为 RUNTIME（防退化）。均为注解 API 自身的行为，
  未触碰 Task 2.2/2.3 的 apply/loadFromHistory 分发覆盖。

## Self-review

- 范围合规：未改 AggregateRoot.java、未改 ddd4j-core/pom.xml、未写 apply 相关测试。
- 提交只含两个新文件；未提交任何无关的未跟踪文档（docs/superpowers/plans/*.md 仍为 untracked）。
- 语义对照参考文档 01-aggregate-root.md：方法级 `ignoreOnReplay()` 正是对 fuin
  `getIgnoredEvents()` 声明 protected final 却称可覆写缺陷的替代方案；RUNTIME retention 为 Task 2.2
  的反射分发（ClassValue 缓存）铺路。
- 无 concerns 遗留，除上文 DomainEventJsonTest 环境性问题需控制器知悉/派发。

## Fixup: jsr310

- Status: **DONE** — commit `bfbd139d` on `feature/2.0.x`（父提交 490d41d9）。即上文「建议由控制器开独立任务处理」的落地。

### Before / After（ddd4j-core/pom.xml，`<!-- ======== 序列化 ======== -->` 块）

Before（误导性注释 + 无 jsr310）：

```xml
<!-- Jackson（JSON 序列化，@JsonIgnore 等注解；J3 中 annotations+jsr310 已内置于 databind） -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-annotations</artifactId>
</dependency>
```

After（注释纠正 + test 作用域 jsr310，无显式 version）：

```xml
<!-- Jackson（JSON 序列化，@JsonIgnore 等注解；jsr310 未内置于 databind，测试作用域单独引入） -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-annotations</artifactId>
</dependency>
<!-- Jackson java.time 支持（仅测试需要：DomainEventJsonTest 的 findAndAddModules 经 SPI 发现；
     主代码 java.time 序列化由 ddd4j-kit JsonKit 自定义序列化器承担） -->
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
    <scope>test</scope>
</dependency>
```

### 版本管理验证（为何不加 `<version>`）

- `ddd4j-dependencies/pom.xml`（ddd4j-core 的 parent）L4665-4671 以 `scope=import` 引入
  `com.fasterxml.jackson:jackson-bom`（`${jackson-bom.version}` = `${jackson.version}` = 2.22.2），
  该 BOM 管理 jsr310 版本，故依赖声明无需 `<version>`。
- `help:evaluate -Dexpression=jackson.version`（ddd4j-core）→ `2.22.2`。
- 但 effective 解析（`dependency:tree '-Dincludes=com.fasterxml.jackson*'`）实际 winner 是链上
  另一处更后位的管理，全家族对齐为：databind `2.21.2`(compile)、jackson-core `2.21.2`(compile)、
  annotations `2.21`(compile)、**jsr310 `2.21.2`(test，直接依赖)**——databind 与 jsr310 严格同版。
  这正说明省略 version 是唯一正确做法：若按 brief 备选方案硬编码 `${jackson.version}`(2.22.2)，
  jsr310 会与 databind(2.21.2) 形成 2.22.2/2.21.2 错配。
- 主代码作用域不受影响：jsr310 仅 test，`dependency:tree` 中为直接子节点 `:test`，compile scope 无它。

### 测试结果

- 定向：`-Dtest='DomainEventJsonTest,EventHandlerTest,CoreIndependenceTest'` → 10/10 绿
  （CoreIndependence 6 + DomainEventJson 1 + EventHandler 3）。
- 全量：`./mvnw -pl ddd4j-core -am test` → **Tests run: 240, Failures: 0, Errors: 0, Skipped: 0**，
  BUILD SUCCESS。上一报告中「预先存在的环境性失败」已消除，全绿门禁恢复。
- 提交只含 ddd4j-core/pom.xml 一个文件（8 insertions, 1 deletion）；docs/superpowers/plans/*.md 仍为 untracked 未动。
