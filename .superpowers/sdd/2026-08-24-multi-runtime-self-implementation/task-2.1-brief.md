### Task 2.1：添加 @EventHandler 注解

**Files:**
- Create: `ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/EventHandler.java`
- Test: `ddd4j/ddd4j-core/src/test/java/io/ddd4j/core/ddd/event/EventHandlerTest.java`

**Interfaces:**
- 消费：Task 1.2（参考文档）
- 产出：`@EventHandler` 注解 API

- [ ] **Step 1: 写失败测试**

Write `EventHandlerTest.java`：

```java
package io.ddd4j.core.ddd.event;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EventHandlerTest {

    @Test
    void annotationIsRuntimeVisible() throws NoSuchMethodException {
        Method method = SampleHandler.class.getDeclaredMethod("onOrderCreated", OrderCreatedEvent.class);
        EventHandler annotation = method.getAnnotation(EventHandler.class);
        assertNotNull(annotation, "method should be annotated with @EventHandler");
        assertEquals(false, annotation.ignoreOnReplay());
    }

    static class SampleHandler {
        @EventHandler
        public void onOrderCreated(OrderCreatedEvent event) {}
    }

    static class OrderCreatedEvent extends DomainEvent<OrderCreatedEvent.OrderId> {
        public record OrderId(String value) implements EntityId {
            @Override public String asString() { return value; }
        }
        public OrderCreatedEvent() { super(new EntityIdPath(new StringEntityId("test"))); }
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j && ./mvnw -pl ddd4j-core test -Dtest=EventHandlerTest`

Expected: FAIL with "cannot find symbol class EventHandler"

- [ ] **Step 3: 实现注解**

Write `EventHandler.java`：

```java
package io.ddd4j.core.ddd.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记聚合根 / 实体内部的领域事件处理器方法。
 *
 * <p>ddd4j-core 的 {@link io.ddd4j.core.ddd.model.AggregateRoot#apply(DomainEvent)}
 * 通过反射调用所有标有此注解的方法，完成事件应用到聚合状态。
 *
 * <h3>使用</h3>
 * <pre>{@code
 * public class Order extends AggregateRoot<OrderId> {
 *     private Money total;
 *
 *     &#64;EventHandler
 *     public void on(OrderCreatedEvent event) {
 *         this.total = event.getTotal();
 *     }
 *
 *     public void pay(Money amount) {
 *         apply(new OrderPaidEvent(id, amount));
 *     }
 * }
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {

    /**
     * 标记此处理器不参与历史事件回放（{@code loadFromHistory} 时跳过）。
     */
    boolean ignoreOnReplay() default false;
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j && ./mvnw -pl ddd4j-core test -Dtest=EventHandlerTest`

Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/EventHandler.java
git add ddd4j-core/src/test/java/io/ddd4j/core/ddd/event/EventHandlerTest.java
git commit -m "feat(core): 新增 @EventHandler 注解"
```

---


---

## Controller context (beyond the plan text)

- Reference doc for this feature: `docs/reference/fuin-api-patterns/01-aggregate-root.md` — read it. It documents fuin's defects this annotation design deliberately fixes: `getIgnoredEvents()` is `protected final` despite javadoc claiming overridable (→ we use a method-level `ignoreOnReplay()` attribute instead); `MethodExecutor` has no Method cache and a `same()` bug at :192 (→ Task 2.2 will use ClassValue caching).
- ddd4j-core current state: `AggregateRoot` has `registerEvent/domainEvents/pullDomainEvents/clearDomainEvents` (no apply/loadFromHistory yet — that's Task 2.2, do NOT implement it now). `DomainEvent<ID extends EntityId>` abstract class exists with eventId/correlationId/causationId fields.
- The plan's test sketch references `OrderCreatedEvent.OrderId` as a record implementing `EntityId` — verify EntityId's actual method surface (`io.ddd4j.core.ddd.event.EntityId`) before writing the test; adapt the test to the real interface (e.g. if EntityId extends AsStringCapable or has more methods, the record must implement them). Trust the real source over the plan sketch.
- Test file location: `ddd4j-core/src/test/java/io/ddd4j/core/ddd/event/EventHandlerTest.java` (same package as the annotation — test needs package access).
- TDD is mandatory: capture RED (compile failure counts as RED for a new class — use `./mvnw -pl ddd4j-core test-compile` output as the RED evidence) then GREEN evidence in your report.
- Commit message: `feat(core): 新增 @EventHandler 注解`
- Working dir: /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j; branch feature/2.0.x; latest commit is the post-rebase HEAD (36809966 or later — check git log).

## Out of scope

- Do NOT touch AggregateRoot.java (Task 2.2)
- Do NOT write AggregateRootApplyTest/EventHandler coverage tests (Tasks 2.2/2.3)
- Do NOT modify ddd4j-core/pom.xml (annotation needs no new deps)

## Report

Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-2.1-report.md` (TDD RED/GREEN evidence with commands+output, files changed, EntityId surface note, self-review). Reply ≤15 lines: Status / commit / one-line test summary / concerns / report path.
