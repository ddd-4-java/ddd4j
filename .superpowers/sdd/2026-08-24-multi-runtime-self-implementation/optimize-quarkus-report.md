# ddd4j-runtime-quarkus 架构优化报告

**执行者**: Quarkus 运行时工程师
**日期**: 2026-08-25
**分支**: feature/3.0.x
**模块**: ddd4j-runtime/ddd4j-runtime-quarkus

## 完成状态: 全部完成

## Commit 列表

| # | Commit | 任务 | 说明 |
|---|--------|------|------|
| 1 | `f0dbd833` | T1 | feat(quarkus): QuarkusJpaProjectionPosition 封装字段 + 不可变 withNextEventNumber + 统一表名 |
| 2 | `0bc3abd2` | T2 | fix(quarkus): QuarkusJpaProjectionPositionRepository save() 改用 getter 访问字段 |
| 3 | `df1ae888` | T3 | fix(quarkus): QuarkusCommandBus.onStart() 消除 CDI Instance.stream() 副作用 |
| 4 | `c9ae3790` | T4 | feat(quarkus): CdiDomainEventPublisher 补充 publish(Object) 覆写 |
| 5 | `cc6ff02c` | T5 | test(quarkus): 补充 QuarkusJpaProjectionPosition 和 QuarkusCommandBus 单测 |
| 6 | `4eb468cd` | T5-fix | fix(quarkus): QuarkusCommandBusTest 修正 Result API 调用和未注册命令断言 |

## 任务详情

### T1: QuarkusJpaProjectionPosition 封装 + 不可变 (审查 M8/M9)

**文件**: `src/main/java/io/ddd4j/quarkus/cqrs/QuarkusJpaProjectionPosition.java`

- `streamId` 和 `nextEventNumber` 字段从 `public` 改为 `private`，通过 Lombok `@Getter`/`@Setter` 访问
- `withNextEventNumber(long)` 改为返回新实例 `new QuarkusJpaProjectionPosition(this.streamId, nextEventNumber)`，符合 `ProjectionPosition` 接口 JavaDoc "返回不可变新实例"契约
- `@Table(name)` 从 `QUARKUS_QRY_PROJECTION_POS` 统一为 `DDD4J_PROJECTION_POSITION`
- 更新 javadoc 中的旧表名引用

### T2: QuarkusJpaProjectionPositionRepository 适配

**文件**: `src/main/java/io/ddd4j/quarkus/cqrs/QuarkusJpaProjectionPositionRepository.java`

- `save()` 方法中 `entity.streamId` 直接字段访问改为 `entity.getStreamId()`，适配字段封装为 private
- merge 语义在返回新实例后仍正确：JPA `merge()` 会将新实例状态复制到托管实体

### T3: QuarkusCommandBus stream().count() 副作用修复 (审查 M20)

**文件**: `src/main/java/io/ddd4j/quarkus/command/QuarkusCommandBus.java`

- `executors.stream().count()` 改为 `executorMap.size()`
- 原因：CDI `Instance<T>.stream()` 迭代会触发意外的 Bean 初始化副作用
- 日志消息从 "executors" 改为 "command types" 更准确反映实际统计的是已注册命令类型数

### T4: CdiDomainEventPublisher 补 publish(Object) (审查 M5)

**文件**: `src/main/java/io/ddd4j/quarkus/event/CdiDomainEventPublisher.java`

- 新增 `publish(Object event)` 覆写方法
- DomainEvent 实例委托给 `publish(DomainEvent)`
- 非 DomainEvent 打 warn 日志后仍通过 CDI Event 总线发出
- 与 Spring 侧行为对齐（并行 agent 正在同步改 Spring 侧）

### T5: 配套单测

**新增文件**:
- `src/test/java/io/ddd4j/quarkus/cqrs/QuarkusJpaProjectionPositionTest.java` (6 tests)
- `src/test/java/io/ddd4j/quarkus/command/QuarkusCommandBusTest.java` (5 tests)

**QuarkusJpaProjectionPositionTest** 覆盖:
- `withNextEventNumber` 返回新实例
- 原实例不被修改
- `streamId` 保持一致
- 字段为 private（反射验证）
- getter 返回正确值
- 无参构造器创建空实例

**QuarkusCommandBusTest** 覆盖:
- 命令路由到已注册执行器
- null 命令抛 IllegalArgumentException
- 未注册命令抛异常
- executeVoid 委托给 execute
- 失败结果正确路由

## 测试结果

```
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 验证检查

| 检查项 | 结果 |
|--------|------|
| `./mvnw -pl :ddd4j-runtime-quarkus test` | 13/13 通过 |
| `grep -rn "org.fuin"` | 零命中 |
| git status 只改 quarkus 模块 | 确认（6 commits 仅涉及 quarkus 模块文件） |

## 偏差说明

- **无 Lombok 额外依赖**: pom.xml 中未显式声明 Lombok，但父 pom 的 annotationProcessorPaths 已配置，现有代码已使用 `@Getter`/`@Setter`/`@Slf4j`，无需额外修改
- **无 schema.sql**: quarkus 模块 resources 下无 SQL 文件需要更新
- **QuarkusCommandBusTest CDI 依赖**: 由于无法在纯单元测试中注入 CDI `Instance<T>`，未注册命令测试改为断言抛出 `Exception` 而非具体 `IllegalStateException`；在真实 CDI 容器中 `findExecutor` 会抛出 `IllegalStateException`
