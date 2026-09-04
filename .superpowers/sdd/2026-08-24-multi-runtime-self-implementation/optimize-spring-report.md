# ddd4j-runtime-spring 架构优化报告

**模块**: `ddd4j-runtime/ddd4j-runtime-spring`
**分支**: `feature/3.0.x`
**日期**: 2026-08-25
**执行人**: Spring 运行时工程师 Agent

## 完成状态

全部 4 个任务已完成，测试全绿，零 fuin 引用。

## Commit 列表

| # | Hash | Message | 类型 |
|---|------|---------|------|
| 1 | `012ed606` | `feat(spring): 新增 SpringCommandBus 实现 CQRS 命令总线` | feat |
| 2 | `ae21a660` | `fix(spring): SpringDomainEventPublisher 补 publish(Object) 覆写` | fix |
| 3 | `d471fbb7` | `fix(spring): SpringJpaProjectionPosition.withNextEventNumber 返回新实例` | fix |
| 4 | `ba48b3c2` | `fix(spring): resetToZero 空方法体澄清注释（审查 M10）` | fix |

## 任务详情

### T1: 新增 SpringCommandBus（审查 M3）

**新增文件**:
- `src/main/java/io/ddd4j/spring/command/SpringCommandBus.java`
- `src/test/java/io/ddd4j/spring/command/SpringCommandBusTest.java`

**设计决策**:
- 直接实现 `CommandBus` 接口（非继承 `DefaultCommandBus`），与 Quarkus `QuarkusCommandBus` 模式对齐
- 使用 `SmartInitializingSingleton` 在容器刷新后扫描 `CommandExecutor` Bean，避免循环依赖
- 方法级 `@Transactional`（参照旧版 743d049d 的教训——类级注解对继承方法不生效）
- `ConcurrentHashMap` 路由表，未注册 command 抛 `IllegalStateException`（消息风格与 Quarkus 对齐）
- 不可 `final`（CGLIB 代理需子类化）

**测试覆盖（5 个）**:
- 注册验证、路由正确性、未注册 command 报错、null 防御、多执行器路由

### T2: SpringDomainEventPublisher 补 publish(Object)（审查 M5）

**修改文件**: `src/main/java/io/ddd4j/spring/event/SpringDomainEventPublisher.java`

**变更**:
- 覆写 `DomainEventPublisher.publish(Object)` 默认 no-op 方法
- `DomainEvent` 实例委托给 `publish(DomainEvent)`
- 非 `DomainEvent` 对象记录 warn 日志，避免静默丢弃
- 与 Quarkus `CdiDomainEventPublisher` 行为对齐

### T3: SpringJpaProjectionPosition 不可变修复（审查 M8）

**修改文件**: `src/main/java/io/ddd4j/spring/cqrs/SpringJpaProjectionPosition.java`
**新增文件**: `src/test/java/io/ddd4j/spring/cqrs/SpringJpaProjectionPositionTest.java`

**变更**:
- `withNextEventNumber(long)` 从修改 `this` 并返回改为 `return new SpringJpaProjectionPosition(this.streamId, nextEventNumber)`
- 遵循 `ProjectionPosition` 接口 JavaDoc"返回不可变新实例"契约
- JPA merge 语义：新实例传给 `repository.save()` 正常工作

**测试覆盖（3 个）**:
- 返回新实例且原对象不变、连续调用独立性、返回类型可转型

### T4: resetToZero 空方法体澄清（审查 M10）

**修改文件**: `src/main/java/io/ddd4j/spring/cqrs/SpringJpaProjectionPositionRepository.java`

**变更**:
- 补充 Javadoc 说明方法体为空是正确的：Spring Data JPA 通过 `@Query` 注解直接执行 JPQL
- `@Modifying` 标记修改型查询，Spring Data 在事务内执行并自动提交
- 保留 `@Query` 机制不变，不破坏现有调用方
- 核心接口声明返回类型为 `void`，无法改为 `int`（不修改 core 模块）

## 测试结果

```
Tests run: 3, Failures: 0, Errors: 0 -- SpringJpaProjectionPositionTest
Tests run: 1, Failures: 0, Errors: 0 -- SpringRuntimeContractTest
Tests run: 4, Failures: 0, Errors: 0 -- SpringDddAnnotationFusionTest
Tests run: 5, Failures: 0, Errors: 0 -- SpringCommandBusTest
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 验收检查

- [x] `./mvnw -pl ddd4j-runtime/ddd4j-runtime-spring test` 全绿（13/13）
- [x] `grep -rn "org.fuin" ddd4j-runtime/ddd4j-runtime-spring --include="*.java"` 零命中
- [x] `git status` 确认只改了 `ddd4j-runtime/ddd4j-runtime-spring` 目录
- [x] 每个逻辑任务一个 commit，message 遵循 `feat(spring):` / `fix(spring):` 约定
- [x] 新文件带 Apache-2.0 header
- [x] 中文 javadoc/注释，与现有代码风格一致

## 偏差与假设

1. **T2 Quarkus 对齐说明**: Quarkus `CdiDomainEventPublisher` 同样未覆写 `publish(Object)`，本次 Spring 实现主动补上，比 Quarkus 更完善。
2. **T4 返回类型**: `resetToZero` 在核心接口声明为 `void`，无法改为 `int` 返回受影响行数（不修改 core 模块），故选择补充注释方案。
3. **pom.xml 未修改**: `spring-tx`、`spring-context` 等依赖已存在于 pom.xml 中，无需新增依赖。
