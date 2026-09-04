### Task 2.3：扩展 AggregateRoot 单元测试覆盖

**Files:**
- Create: `ddd4j/ddd4j-core/src/test/java/io/ddd4j/core/ddd/model/AggregateRootEventHandlerTest.java`

- [ ] **Step 1: 写更多测试用例**

```java
package io.ddd4j.core.ddd.model;

import io.ddd4j.core.ddd.event.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AggregateRootEventHandlerTest {

    static class Counter extends AggregateRoot<String> {
        int count = 0;
        boolean sideEffectRan = false;

        @EventHandler
        public void on(IncrementEvent e) { count++; }

        @EventHandler(ignoreOnReplay = true)
        public void onSideEffect(IncrementEvent e) { sideEffectRan = true; }

        public Counter() {}

        public void trigger() { apply(new IncrementEvent()); }
    }

    static class IncrementEvent extends DomainEvent<IncrementEvent.Id> {
        public record Id(String value) implements EntityId {
            @Override public String asString() { return value; }
        }
        public IncrementEvent() { super(new EntityIdPath(new StringEntityId("c"))); }
    }

    @Test
    void eventHandlerInvokedOnce() {
        Counter c = new Counter();
        c.trigger();
        assertEquals(1, c.count);
    }

    @Test
    void eventHandlerInvokedForEachTrigger() {
        Counter c = new Counter();
        c.trigger();
        c.trigger();
        c.trigger();
        assertEquals(3, c.count);
    }

    @Test
    void ignoreOnReplayHandlerNotInvokedOnLoad() {
        Counter c = new Counter();
        c.loadFromHistory(List.of(new IncrementEvent()));
        assertEquals(1, c.count);
        assertFalse(c.sideEffectRan);
    }

    @Test
    void ignoreOnReplayHandlerInvokedOnApply() {
        Counter c = new Counter();
        c.trigger();
        assertTrue(c.sideEffectRan);
    }

    @Test
    void handlerCacheReused() {
        Counter c1 = new Counter();
        Counter c2 = new Counter();
        c1.trigger();
        c2.trigger();
        assertEquals(1, c1.count);
        assertEquals(1, c2.count);
    }

    @Test
    void privateHandlerAccessible() {
        Counter c = new Counter();
        c.trigger();
        assertEquals(1, c.count);
    }
}
```

- [ ] **Step 2: 跑测试**

Run: `cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j && ./mvnw -pl ddd4j-core test -Dtest=AggregateRootEventHandlerTest`

Expected: PASS（全部 6 个用例）

- [ ] **Step 3: 提交**

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add ddd4j-core/src/test/java/io/ddd4j/core/ddd/model/AggregateRootEventHandlerTest.java
git commit -m "test(core): AggregateRoot 事件处理器全覆盖"
```

---


---

## Controller context

1. Task 2.2 已落地（commit a551fb6c）：`AggregateRoot` 现有 `apply(E)`/`loadFromHistory(List)` + 双 ClassValue 缓存；`AggregateRootApplyTest`（5 测试）在 `io.ddd4j.core.ddd.model` 包。**先读这两个文件再写**，新测试类 `AggregateRootEventHandlerTest` 与之同包，风格一致（含 `@since 2.0.x`）。
2. 本任务 = 计划的 6 用例（Counter/IncrementEvent sketch）**外加** Task 2.2 评审并入的 2 个补强用例（共 8）：
   - 【并入 A】私有 `private void on(...)` handler 的可达性（javadoc 宣告 private 可用但现有测试未覆盖——setAccessible 路径）
   - 【并入 B】`applyThrowsWhenNoHandlerRegistered` 场景补 `domainEvents().isEmpty()` 断言（失败不入队的显式验证）
   - 计划 6 用例中 `privateHandlerAccessible` 与【并入 A】合并为一个用例即可（勿重复）——最终 ≥7 用例
3. 测试聚合/事件写法照抄 AggregateRootApplyTest 的实体 ID record 模式（EntityId 3 方法面）；Counter 可直接 extends AggregateRoot<String> 并实现 id()。
4. **门禁**：`./mvnw -pl ddd4j-core -am test` 全绿（基线 245 → ≥252）；ArchUnit 绿；无新依赖；构建需 `-am`。
5. 单 commit：`test(core): AggregateRoot 事件处理器全覆盖`。只新增测试文件，不动主代码（若发现主代码 bug → BLOCKED 上报，勿自行改）。

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-2.3-report.md`（用例清单与逐条对应、RED 不适用说明——纯测试任务无 RED 阶段、全量测试数、self-review）。Reply ≤15 lines.
