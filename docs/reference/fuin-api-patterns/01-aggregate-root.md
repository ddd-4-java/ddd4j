# 01. fuin API 模式：聚合根反射事件应用

> 对应 README 索引第 01 项；只读提炼 fuin 设计，ddd4j 全部自研实现（no code reuse）。

## 来源

- 仓库：https://github.com/fuinorg/ddd-4-java
- 版本：0.7.0（本地快照：`workspace-ddd4j-boot/ddd-4-java`）
- 文件：
  - `core/src/main/java/org/fuin/ddd4j/core/AbstractAggregateRoot.java:36-238`（主源）
  - `core/src/main/java/org/fuin/ddd4j/core/AggregateRoot.java:31-101`（接口契约）
  - `core/src/main/java/org/fuin/ddd4j/core/AbstractEntity.java:33-101`（子实体伴生抽象）
  - `core/src/main/java/org/fuin/ddd4j/core/DomainEvent.java:29-63`（事件接口）
- 关键 API：
  - `apply`（AbstractAggregateRoot.java:187-193）：应用新事件——反射分发到 `@ApplyEvent` 方法并登记未提交事件。
  - `loadFromHistory`（AbstractAggregateRoot.java:107-129）：按序回放历史事件，每应用一件 `version++`。
  - `callAnnotatedEventHandlerMethodOnAggregateRootOrChild`（AbstractAggregateRoot.java:141-170）：按 `EntityIdPath` 路由到子实体。
  - `callAnnotatedEventHandlerMethod`（AbstractAggregateRoot.java:224-236）：反射查找并调用 `@ApplyEvent` 处理器。
  - `markChangesAsCommitted`/`getVersion`/`getNextVersion`/`getNextApplyVersion`（AbstractAggregateRoot.java:86-104）：版本三件套。

## fuin 的设计

核心机制：命令方法调用 `apply(event)`，由注解驱动的反射把事件分发到处理器方法；回放走 `loadFromHistory`，只改状态、不产生未提交事件。

**1）新事件应用——apply()（AbstractAggregateRoot.java:187-193）**

```java
protected final void apply(@NotNull final DomainEvent<?> event) {
    if (callAnnotatedEventHandlerMethod(this, event)) {
        uncommitedChanges.add(event);
    } else {
        throw new IllegalStateException("Couldn't find an event handler for: " + event.getClass().getName());
    }
}
```

命令方法内 `apply(new OrderPlaced(...))` 即完成「改状态 + 记事件」；找不到处理器立即 fail-fast。

**2）历史事件回放——loadFromHistory（AbstractAggregateRoot.java:115-129）**

```java
public final void loadFromHistory(final List<DomainEvent<?>> history) {
    if (history == null) {
        return;
    }
    for (final DomainEvent<?> event : history) {
        if (!getIgnoredEvents().contains(event.getClass())) {
            final boolean applied = callAnnotatedEventHandlerMethodOnAggregateRootOrChild(this, event);
            if (applied) {
                version++;
            } else {
                throw new IllegalStateException("Wasn't able to apply historic event '" + event + "' to: " + this.getClass().getName());
            }
        }
    }
}
```

回放与新事件双通道分离：回放直接 `version++`，绕过 `uncommitedChanges`，避免重建的聚合被再次持久化。

**3）反射分发——callAnnotatedEventHandlerMethod（AbstractAggregateRoot.java:224-236）**

```java
static boolean callAnnotatedEventHandlerMethod(final Entity<?> entity, final DomainEvent<?> event) {

    Contract.requireArgNotNull("entity", entity);
    Contract.requireArgNotNull("event", event);

    final Method method = METHOD_EXECUTOR.findDeclaredAnnotatedMethod(entity, ApplyEvent.class, event.getClass());
    if (method == null) {
        return false;
    }
    METHOD_EXECUTOR.invoke(method, entity, event);
    return true;

}
```

以「单参数类型 == 事件类型 + `@ApplyEvent` 注解」匹配处理器（MethodExecutor.java:82-99），从具体类向上扫描到 `AbstractAggregateRoot` 为止（MethodExecutor.java:111-129）。

**4）子实体路由——按 EntityIdPath 递归定位（AbstractAggregateRoot.java:141-170）**

回放时 `callAnnotatedEventHandlerMethodOnAggregateRootOrChild` 先取 `event.getEntityIdPath()`：首段若不是聚合根 ID 立即抛 `IllegalStateException`（:146-149）；路径只剩一段时直接调根的处理器（:151-154）；否则从根开始，逐段用 `@ChildEntityLocator` 注解方法（按 ID 类型匹配，:160-161）反射下钻到目标子实体，最后调它的处理器（:169）。子实体事件回放因此无需子实体自建事件流。

**5）子实体事件统一经根登记——AbstractEntity.apply（AbstractEntity.java:56-58）**

```java
protected final void apply(@NotNull final DomainEvent<?> event) {
    root.applyNewChildEvent(this, event);
}
```

子实体不持有事件列表，一律委托根的 `applyNewChildEvent`（AbstractAggregateRoot.java:203-211），保证 `uncommittedChanges` 单一来源。

版本语义：`version` 从 -1 起（AbstractAggregateRoot.java:40）；`getNextVersion() = version + uncommitedChanges.size()`（:97-99）供乐观锁用；`getNextApplyVersion()`（:102-104）供构造事件时取「本事件落库后的版本」。

## 优点（值得借鉴的）

- 注解 + 反射分发（`@ApplyEvent` 单参方法匹配）：聚合子类零 if-else/switch 样板，新增事件类型只加一个处理器方法。
- apply 与 loadFromHistory 双通道严格分离：回放只变更状态与版本、绝不写入 `uncommitedChanges`，从机制上杜绝「重建即重复持久化」。
- 子实体事件一律经根登记（`AbstractEntity.apply` → `root.applyNewChildEvent`）：未提交事件单一来源，仓储只需与根交互。
- `EntityIdPath` 驱动回放路由（`@ChildEntityLocator` 逐级下钻）：子实体事件回放无需子实体自建事件流，路径首段强制为聚合根 ID，错误路径早失败。
- 版本三件套语义清晰：`getVersion`（已提交）/`getNextVersion`（含未提交，供乐观锁比对）/`getNextApplyVersion`（构造新事件用），一个 int 字段承载三种视角。
- equals/hashCode 基于 ID 且 `final`（AbstractAggregateRoot.java:52-73）：聚合同一性由标识决定，杜绝子类按可变属性改写。

## 缺点（应规避的）

- `getIgnoredEvents()` 声明为 `protected final`（AbstractAggregateRoot.java:177）却声称「Subclasses can overwrite」（:173）——子类实际无法覆写，「忽略废弃历史事件」机制形同虚设。
- `MethodExecutor` 无任何方法缓存：每次 apply/回放都 `getDeclaredMethods()` 全类层次线性扫描（MethodExecutor.java:111-129），事件多的聚合回放是 O(事件数 × 类层次 × 方法数)；且 `same()` 用 `expected[0] != actual[i]`（MethodExecutor.java:192）误把首参类型与每个实参比较，多参匹配结果是错的。
- 契约校验依赖第三方：`jakarta.validation.constraints.@NotNull` + `org.fuin.objects4j.common.Contract`（AbstractAggregateRoot.java:20-21、226-227）——ddd4j 用 JSpecify + `Objects.requireNonNull`，核心零第三方契约依赖。
- `version` 裸 int + `-1` 魔法数（:40、:98）：对外却又有 `AggregateVersion` 值对象（:102-104），两套表示并存易错。
- `AggregateRoot` 接口暴露 `getUncommittedChanges()`/`markChangesAsCommitted()`（AggregateRoot.java:47、59）等持久化关注点——领域模型接口与仓储协议耦合，任何持有聚合引用的代码都能感知「提交」概念。
- equals 中 `getClass() != obj.getClass()` 严格类相等 + 无检查强转 `(AbstractAggregateRoot<?>) obj`（:68-72）：对代理（懒加载/ES 重建）与同 ID 不同加载形态脆弱。

## ddd4j 自研决策

原则：**继承 + 扩展** ddd4j-core 现有 `AggregateRoot`（`io/ddd4j/core/ddd/model/AggregateRoot.java`），不重写。

- **借鉴**：
  - 注解标记处理器 + 反射分发——落地阶段 2 Task 2.1（`@EventHandler`）+ Task 2.2（`AggregateRoot.apply(DomainEvent)`）；
  - apply/loadFromHistory 双通道分离、回放绕过未提交列表——Task 2.2 实现时作为硬性测试断言（Task 2.3）；
  - 子实体事件统一经根登记——与 ddd4j 现有 `registerEvent()`/`domainEvents()` 单列表模式天然一致，直接沿用；
  - `EntityIdPath` 驱动子实体路由——ddd4j `DomainEvent` 已有 `entityIdPath`/`getEntityId()`（`io/ddd4j/core/ddd/event/DomainEvent.java:97-98、217-229`），无需新概念。
- **改写**：
  - 注解 `@ApplyEvent` → `@EventHandler`，忽略历史事件从「聚合级 `getIgnoredEvents()`（且是坏的 final）」改为方法级 `ignoreOnReplay()` 属性（Task 2.1）；
  - 反射查找每次全扫描 → `ClassValue` 缓存 `Method`（ddd4j `DomainEvent` 已有 `ClassValue<EventType>` 先例，DomainEvent.java:54-59）；
  - 找不到处理器抛 `IllegalStateException` 保留，但异常消息携带 `EntityIdPath` 与事件类型，便于定位（Task 2.2）；
  - 版本对外统一 `AggregateVersion` 值对象，`-1` 起始语义封装为常量。
- **不借鉴**：
  - `objects4j Contract` / `jakarta.validation` 注解——ddd4j-core 保持 JSpecify + JDK 断言；
  - 泛型约束 `<ID extends AggregateRootId>`——ddd4j 现有 `AggregateRoot<ID extends Serializable>` 签名不动，保证 2.0.x 兼容；
  - `final` 化 equals/hashCode 并强制收编——ddd4j `Entity` 已定义同一性约定，不破坏现有子类；
  - 接口层暴露 `markChangesAsCommitted()` 等提交语义——ddd4j 保持 `pullDomainEvents()`/`clearDomainEvents()` 既有 API，提交动作留在仓储侧。

## 落地计划

- [ ] Task 2.1：新增 `@EventHandler` 注解（含 `ignoreOnReplay()` 属性）——替代 `@ApplyEvent` + 修复 `getIgnoredEvents()` 缺陷的方法级方案。
- [ ] Task 2.2：扩展 ddd4j-core `AggregateRoot`，实现 `apply()`/`loadFromHistory()` + `ClassValue` 处理器方法缓存。
- [ ] Task 2.3：`AggregateRootApplyTest` 覆盖：新事件登记、回放不产生未提交事件、`ignoreOnReplay` 跳过、找不到处理器异常。
- [ ] Task 2.4：写 ADR-0006（反射事件应用机制），引用本文档的借鉴/改写/不借鉴结论。
- [ ] Task 2.5：ArchUnit 强化——保证 ddd4j-core 不引入 `objects4j`/`jakarta.validation` 等第三方契约依赖。
- [ ] Task 2.6：阶段 2 全量验证（`./mvnw verify -pl ddd4j-core`）。
