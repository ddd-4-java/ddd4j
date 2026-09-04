### Task 2.2：扩展 AggregateRoot.apply() 反射实现

**Files:**
- Modify: `ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/model/AggregateRoot.java`
- Test: `ddd4j/ddd4j-core/src/test/java/io/ddd4j/core/ddd/model/AggregateRootApplyTest.java`

**Interfaces:**
- 消费：Task 2.1 的 `@EventHandler`
- 产出：`AggregateRoot.apply(DomainEvent)` / `loadFromHistory(List)` 方法

- [ ] **Step 1: 写失败测试**

Write `AggregateRootApplyTest.java`：

```java
package io.ddd4j.core.ddd.model;

import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityId;
import io.ddd4j.core.ddd.event.EntityIdPath;
import io.ddd4j.core.ddd.event.EventHandler;
import io.ddd4j.core.ddd.event.StringEntityId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateRootApplyTest {

    static class Order extends AggregateRoot<String> {
        private String status;
        public Order(String id) {}

        @EventHandler
        public void on(OrderCreatedEvent event) {
            this.status = "CREATED";
        }
    }

    static class OrderCreatedEvent extends DomainEvent<OrderCreatedEvent.OrderId> {
        public record OrderId(String value) implements EntityId {
            @Override public String asString() { return value; }
        }
        public OrderCreatedEvent() { super(new EntityIdPath(new StringEntityId("order-1"))); }
    }

    @Test
    void applyInvokesAnnotatedHandler() {
        Order order = new Order("order-1");
        OrderCreatedEvent event = new OrderCreatedEvent();
        order.apply(event);
        assertEquals("CREATED", order.status);
        assertTrue(order.domainEvents().contains(event));
    }

    @Test
    void loadFromHistoryRebuildsAggregate() {
        Order order = new Order("order-1");
        order.loadFromHistory(List.of(new OrderCreatedEvent()));
        assertEquals("CREATED", order.status);
        assertTrue(order.domainEvents().isEmpty());
    }

    @Test
    void applyThrowsWhenNoHandlerRegistered() {
        Order order = new Order("order-1");
        assertThrows(IllegalStateException.class, () ->
            order.apply(new UnhandledEvent())
        );
    }

    static class UnhandledEvent extends DomainEvent<OrderCreatedEvent.OrderId> {
        public UnhandledEvent() { super(new EntityIdPath(new StringEntityId("x"))); }
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j && ./mvnw -pl ddd4j-core test -Dtest=AggregateRootApplyTest`

Expected: FAIL with "cannot find symbol method apply(DomainEvent)"

- [ ] **Step 3: 实现 apply + loadFromHistory**

Modify `AggregateRoot.java`，添加以下代码（在 `protected void registerEvent(DDomainEvent<?> event)` 之后）：

```java
    /**
     * 注册并应用领域事件。
     *
     * <p>通过反射调用所有标有 {@link EventHandler} 的方法，并验证 aggregateVersion 连贯性。
     * 应用成功后，事件会进入未提交事件列表（{@link #domainEvents()}）。
     *
     * @param event 要应用的事件
     * @param <E> 事件类型
     * @return 应用成功的事件
     * @throws IllegalStateException 找不到对应的 @EventHandler 方法
     */
    @SuppressWarnings("unchecked")
    protected <E extends DomainEvent<?>> E apply(E event) {
        Objects.requireNonNull(event, "event must not be null");
        java.util.Map<Class<?>, java.lang.reflect.Method> handlers =
            AGGREGATE_HANDLER_CACHE.get(this.getClass());
        java.lang.reflect.Method handler = handlers.get(event.getClass());
        if (handler == null) {
            throw new IllegalStateException(
                "No @EventHandler method found for event type: "
                    + event.getClass().getName()
                    + " in aggregate: " + this.getClass().getName());
        }
        try {
            handler.setAccessible(true);
            handler.invoke(this, event);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke @EventHandler for "
                + event.getClass().getName(), e);
        }
        mutableDomainEvents().add(event);
        return event;
    }

    /**
     * 从历史事件流重建聚合根。
     *
     * <p>批量应用所有历史事件，跳过 {@link EventHandler#ignoreOnReplay()} 标记的处理器。
     * 重建完成后，未提交事件列表为空。
     *
     * @param history 历史事件流
     */
    public final void loadFromHistory(java.util.List<? extends DomainEvent<?>> history) {
        if (Objects.isNull(history)) {
            return;
        }
        java.util.Map<Class<?>, java.lang.reflect.Method> handlers =
            AGGREGATE_REPLAY_CACHE.get(this.getClass());
        for (DomainEvent<?> event : history) {
            java.lang.reflect.Method handler = handlers.get(event.getClass());
            if (handler != null) {
                try {
                    handler.setAccessible(true);
                    handler.invoke(this, event);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Failed to replay event "
                        + event.getClass().getName(), e);
                }
            }
        }
        clearDomainEvents();
    }

    /** 双层 ClassValue 缓存：(聚合类 → (事件类型 → @EventHandler Method)) - apply 用 */
    private static final ClassValue<java.util.Map<Class<?>, java.lang.reflect.Method>> AGGREGATE_HANDLER_CACHE
        = new ClassValue<>() {
            @Override
            protected java.util.Map<Class<?>, java.lang.reflect.Method> computeValue(Class<?> aggregateType) {
                java.util.Map<Class<?>, java.lang.reflect.Method> map = new java.util.HashMap<>();
                scanHandlers(aggregateType, map, false);
                return map;
            }
        };

    /** 双层 ClassValue 缓存 - loadFromHistory 用（跳过 ignoreOnReplay） */
    private static final ClassValue<java.util.Map<Class<?>, java.lang.reflect.Method>> AGGREGATE_REPLAY_CACHE
        = new ClassValue<>() {
            @Override
            protected java.util.Map<Class<?>, java.lang.reflect.Method> computeValue(Class<?> aggregateType) {
                java.util.Map<Class<?>, java.lang.reflect.Method> map = new java.util.HashMap<>();
                scanHandlers(aggregateType, map, true);
                return map;
            }
        };

    private static void scanHandlers(Class<?> aggregateType,
                                      java.util.Map<Class<?>, java.lang.reflect.Method> map,
                                      boolean skipIgnored) {
        Class<?> current = aggregateType;
        while (current != null && current != Object.class) {
            for (java.lang.reflect.Method m : current.getDeclaredMethods()) {
                if (m.isAnnotationPresent(EventHandler.class)) {
                    EventHandler ann = m.getAnnotation(EventHandler.class);
                    if (skipIgnored && ann.ignoreOnReplay()) {
                        continue;
                    }
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 1 && DomainEvent.class.isAssignableFrom(params[0])) {
                        @SuppressWarnings("unchecked")
                        Class<? extends DomainEvent<?>> eventType =
                            (Class<? extends DomainEvent<?>>) params[0];
                        map.putIfAbsent(eventType, m);
                    }
                }
            }
            current = current.getSuperclass();
        }
    }
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j && ./mvnw -pl ddd4j-core test -Dtest=AggregateRootApplyTest`

Expected: PASS

- [ ] **Step 5: 跑全量测试确保无回归**

Run: `cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j && ./mvnw -pl ddd4j-core test`

Expected: 全部测试通过 + ArchUnit CoreIndependenceTest 通过

- [ ] **Step 6: 提交**

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add ddd4j-core/src/main/java/io/ddd4j/core/ddd/model/AggregateRoot.java
git add ddd4j-core/src/test/java/io/ddd4j/core/ddd/model/AggregateRootApplyTest.java
git commit -m "feat(core): AggregateRoot.apply/loadFromHistory 反射实现"
```

---


---

## Controller context (beyond the plan text — supersedes stale parts of the sketch)

1. **Task 2.1 已落地**：`io.ddd4j.core.ddd.event.EventHandler` 注解存在（RUNTIME/METHOD/`ignoreOnReplay() default false`），测试 `EventHandlerTest` 展示了 EntityId 真实 3 方法面（getType/asString/asTypedString）的 record 实现写法——照抄该写法构建测试事件 ID。
2. **AggregateRoot 现状（务必先读再改）**：`ddd4j-core/src/main/java/io/ddd4j/core/ddd/model/AggregateRoot.java` 已有 `registerEvent/domainEvents/pullDomainEvents/clearDomainEvents/mutableDomainEvents`（私有）与 `repository()`。新增方法**插入在「事件管理」区块内**（registerEvent 之前或之后），保持 4 空格缩进与现有 javadoc 风格（含 `<h3>` 用法示例、`@author PartMe.AI`、`@since`）。类上 `@SuppressWarnings({"unchecked", "rawtypes"})` 已存在，新代码若需可局部再加。
3. **计划 sketch 的两处已知缺陷，按此修正实现**（review 依据）：
   - sketch 的 `apply` 里有一段被自己否决的 static `findHandlerInHierarchy`（返回 null 的死代码）——**不要照抄**；实现只保留双层 ClassValue 方案：`AGGREGATE_HANDLER_CACHE`（skipIgnored=false）+ `AGGREGATE_REPLAY_CACHE`（skipIgnored=true），均为 `ClassValue<Map<Class<?>, Method>>`，`scanHandlers(Class<?>, Map, boolean)` 自聚合类向上遍历超类收集 `@EventHandler` 单参 `DomainEvent` 子类型方法，`putIfAbsent` 保持子类优先。
   - `apply` 签名按 sketch：`protected <E extends DomainEvent<?>> E apply(E event)`——校验非空→查缓存 Map→无 handler 抛 `IllegalStateException`（消息含事件类型与聚合类型全名）→`setAccessible(true)+invoke`→`ReflectiveOperationException` 包成 `IllegalStateException`→成功后 `mutableDomainEvents().add(event)`→返回 event。**不做 aggregateVersion 连贯性校验**（javadoc 注明版本校验留待阶段 3 EventStore 乐观锁，本任务只做事件应用）。
   - `loadFromHistory`：`public final void loadFromHistory(List<? extends DomainEvent<?>> history)`，null 安全直接 return；逐事件查 REPLAY 缓存（无 handler 静默跳过——历史事件允许本聚合不关心）；invoke 异常包 IllegalStateException；结束后 `clearDomainEvents()`。
   - `mutableDomainEvents()` 是现有私有方法——直接复用，勿新建。
4. **测试适配**（TDD，RED=compile failure of `order.apply(event)`）：
   - 测试类 `AggregateRootApplyTest` 放 `ddd4j-core/src/test/java/io/ddd4j/core/ddd/model/`。
   - sketch 里 `Order extends AggregateRoot<String>` 但 AggregateRoot 是 `AggregateRoot<ID extends Serializable> implements Entity<ID>`——**读 Entity 接口**，测试聚合按最省方式满足（可给 `id()` 返回 null 或实现之；以编译通过且语义清晰为准，报告里说明选择）。
   - 断言集（至少）：apply 触发 @EventHandler 改状态 + 事件进 domainEvents；无 handler 抛 IllegalStateException；loadFromHistory 重建状态且 **domainEvents 为空**（回放不入队——这是 01 篇记录的 fuin 缺陷的对位设计）；`@EventHandler(ignoreOnReplay=true)` 在 loadFromHistory 跳过、apply 正常执行。
5. **门禁**：`./mvnw -pl ddd4j-core -am test` 全绿（当前基线 240 tests，本任务后 ≥244）；ArchUnit CoreIndependenceTest 必须仍绿（不新增任何依赖）。构建需 `-am`。
6. 单 commit：`feat(core): AggregateRoot.apply/loadFromHistory 反射实现`。

## Out of scope
- 不写 AggregateRootEventHandlerTest 全覆盖（Task 2.3）；不动 pom；不动 EventHandler 注解本身；不实现版本校验/EventStore。

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-2.2-report.md`（RED/GREEN 证据、实现要点与 sketch 偏差清单、Entity 适配说明、全量测试数、self-review）。Reply ≤15 lines。
