### Task 3.3：EventPayloadSerializer 抽象（Jackson + 字节码生成器）

**Files:**
- Create: `ddd4j/ddd4j-data/ddd4j-data-event-store/src/main/java/io/ddd4j/data/eventstore/jackson/EventPayloadSerializer.java`

**Interfaces:**
- 消费：Task 3.2
- 产出：Jackson 序列化器抽象

- [ ] **Step 1: 写 EventPayloadSerializer**

Write `EventPayloadSerializer.java`：

```java
package io.ddd4j.data.eventstore.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import io.ddd4j.core.ddd.event.DomainEvent;

import java.io.IOException;
import java.util.Objects;

/**
 * 领域事件 payload Jackson 序列化器。
 *
 * <p>使用 Jackson + 默认类型信息（{@code @class}）支持多态反序列化。
 * 跨运行时共享：Spring / Quarkus / Micronaut / Helidon / Javalin / Vert.x / Dropwizard 都用 Jackson 2.22.x。
 */
public class EventPayloadSerializer {

    private final ObjectMapper objectMapper;

    public EventPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.objectMapper.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(DomainEvent.class)
                .build(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
        );
    }

    public String serialize(DomainEvent<?> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }

    public DomainEvent<?> deserialize(String json, Class<? extends DomainEvent<?>> eventType) {
        try {
            return objectMapper.readValue(json, eventType);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize event", e);
        }
    }
}
```

- [ ] **Step 2: 验证 + 提交**

Run: `cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j && ./mvnw -pl ddd4j-data/ddd4j-data-event-store compile`

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add ddd4j-data/ddd4j-data-event-store/src/main/java/io/ddd4j/data/eventstore/jackson/
git commit -m "feat(data): EventPayloadSerializer Jackson 多态序列化"
```

---


---

## Controller context（修正 sketch 一处关键缺陷）

1. **缺陷修正（必须）**：sketch 在构造器里对**传入的** ObjectMapper 调 activateDefaultTyping——副作用污染调用方共享 mapper（如 Spring 全局 mapper 会被强加多态 typing，安全/行为隐患）。正确做法：`this.objectMapper = Objects.requireNonNull(source).copy();` 然后**在副本上** activateDefaultTyping（ObjectMapper.copy() 存在且是 Jackson 惯用隔离法）。javadoc 注明「构造后 source 不受影响」。
2. typing 配置照 sketch：`activateDefaultTyping(BasicPolymorphicTypeValidator.builder().allowIfBaseType(DomainEvent.class).build(), ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)`；序列化 writeValueAsString、反序列化 readValue(json, eventType)；异常包 IllegalStateException（cause 保留），catch 范围用 JacksonException（对齐 ddd4j-kit JsonKit 风格，先读 kit 确认 catch 类型再写）。
3. **测试**（EventPayloadSerializerTest，同包 test）：至少 4 用例——(a) 具体事件 round-trip（含 ZonedDateTime 时间戳：测试基 mapper 需注册 JavaTimeModule → **模块 pom 加 jackson-datatype-jsr310 test 作用域**（BOM 管无版本，先例 ddd4j-core fixup）；serializer.copy 会带上该 module）；(b) 序列化 JSON 含 `@class` 多态标记（As.PROPERTY 验证）；(c) **源 mapper 隔离**：构造 serializer 后，用 source mapper 序列化同一事件，断言输出**不含** `@class`（证无污染）；(d) 反序列化坏 JSON 抛 IllegalStateException。事件 fixture 照 StoredEventTest 的 EntityId record 模式。
4. 门禁：`./mvnw -pl ddd4j-data/ddd4j-data-event-store -am install` BUILD SUCCESS（模块测试数计入报告）；ArchUnit 3 规则仍绿（jackson 已在 allowlist）。
5. 单 commit：`feat(data): EventPayloadSerializer Jackson 多态序列化`。

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-3.3-report.md`。Reply ≤15 lines.
