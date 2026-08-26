/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.core.ddd.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link EntityIdPath} 反序列化与转义测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
class EntityIdPathTest {

    @AfterEach
    void cleanup() {
        // 清理注册表，避免污染其他测试
        EntityIdRegistry.unregister("OrderId");
        EntityIdRegistry.unregister("CustomerId");
    }

    @Test
    void valueOfParsesSingleAndMultiSegmentPaths() {
        EntityIdPath single = EntityIdPath.valueOf("String:order-1");
        assertThat(single.size()).isEqualTo(1);
        assertThat(single.last().asString()).isEqualTo("order-1");

        EntityIdPath multi = EntityIdPath.valueOf("String:order-1/String:item-9");
        assertThat(multi.size()).isEqualTo(2);
        assertThat(multi.first().asString()).isEqualTo("order-1");
        assertThat(multi.last().asString()).isEqualTo("item-9");
    }

    @Test
    void valueOfRejectsMalformedPaths() {
        // 空串 / 空白
        assertThatIllegalArgumentException().isThrownBy(() -> EntityIdPath.valueOf(""));
        assertThatIllegalArgumentException().isThrownBy(() -> EntityIdPath.valueOf("   "));
        // 段内无 ':'（消息含出错段原文）
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EntityIdPath.valueOf("order-1"))
                .withMessageContaining("order-1");
        // 空段（尾部 '/'）
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EntityIdPath.valueOf("String:order-1/"))
                .withMessageContaining("String:order-1/");
        // 段内空 type
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EntityIdPath.valueOf(":order-1"))
                .withMessageContaining(":order-1");
        // 段内空 value
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EntityIdPath.valueOf("String:"))
                .withMessageContaining("String:");
    }

    @Test
    void valueOfIsIdempotentForSerializedForm() {
        EntityIdPath original = new EntityIdPath(new StringEntityId("order-1"), new StringEntityId("item-9"));

        assertThat(EntityIdPath.valueOf(original.asString()).asString()).isEqualTo(original.asString());
    }

    @Test
    void unregisteredTypeFallsBackToStringEntityId() {
        EntityIdPath path = EntityIdPath.valueOf("Unknown:abc-123");

        assertThat(path.size()).isEqualTo(1);
        assertThat((Object) path.last()).isInstanceOf(StringEntityId.class);
        assertThat(path.last().asString()).isEqualTo("abc-123");
    }

    @Test
    void registeredCustomEntityIdIsRestored() {
        // 注册自定义 OrderId 工厂
        EntityIdRegistry.register("OrderId", value -> new TestOrderId(value));

        EntityIdPath path = EntityIdPath.valueOf("OrderId:o-42");

        assertThat(path.size()).isEqualTo(1);
        assertThat((Object) path.last()).isInstanceOf(TestOrderId.class);
        assertThat(path.last().asString()).isEqualTo("o-42");
        assertThat(path.last().getType().asString()).isEqualTo("TestOrder");
    }

    @Test
    void registeredCustomEntityIdRoundTrips() {
        EntityIdRegistry.register("OrderId", value -> new TestOrderId(value));
        EntityIdRegistry.register("CustomerId", value -> new TestCustomerId(value));

        EntityIdPath original = new EntityIdPath(
                new TestOrderId("o-1"),
                new TestCustomerId("c-9"));

        EntityIdPath restored = EntityIdPath.valueOf(original.asString());

        assertThat(restored.size()).isEqualTo(2);
        assertThat((Object) restored.first()).isInstanceOf(TestOrderId.class);
        assertThat(restored.first().asString()).isEqualTo("o-1");
        assertThat((Object) restored.last()).isInstanceOf(TestCustomerId.class);
        assertThat(restored.last().asString()).isEqualTo("c-9");
    }

    @Test
    void valuesContainingColonAreNotEscaped() {
        // 值内含 ':' 无需转义——通过「首个 ':' 切分」约定天然支持
        EntityIdPath path = new EntityIdPath(new TestOrderId("o:1"));
        String serialized = path.asString();

        // 序列化产物保留原始 ':'（在 type/value 分隔符之后的所有 ':' 归属 value）
        assertThat(serialized).doesNotContain("\\");

        EntityIdPath restored = EntityIdPath.valueOf(serialized);
        assertThat(restored.last().asString()).isEqualTo("o:1");
    }

    @Test
    void simplePathsAreNotEscaped() {
        // 简单路径（值不含段分隔符 / 或反斜杠）保持原样输出，无视觉噪音
        EntityIdPath path = new EntityIdPath(new TestOrderId("o-1"));
        String serialized = path.asString();

        assertThat(serialized).isEqualTo("OrderId:o-1");
        assertThat(serialized).doesNotContain("\\");
    }

    @Test
    void valuesContainingPathSeparatorAreEscapedAndRecovered() {
        // 值内含 '/'（段分隔符）：序列化转义为 '\/'，反序列化还原
        EntityIdPath path = new EntityIdPath(new TestOrderId("o/1"));
        String serialized = path.asString();

        // 值内的 '/' 必须被转义为 '\/'
        assertThat(serialized).contains("\\/");

        EntityIdPath restored = EntityIdPath.valueOf(serialized);
        assertThat(restored.last().asString()).isEqualTo("o/1");
    }

    @Test
    void valuesContainingBackslashAreEscapedAndRecovered() {
        EntityIdPath path = new EntityIdPath(new TestOrderId("a\\b"));
        String serialized = path.asString();

        // 反斜杠转义为双反斜杠
        assertThat(serialized).contains("\\\\");

        EntityIdPath restored = EntityIdPath.valueOf(serialized);
        assertThat(restored.last().asString()).isEqualTo("a\\b");
    }

    @Test
    void registryRefusesToUnregisterBuiltinStringType() {
        // 试图移除 StringEntityId 不应生效
        EntityIdRegistry.unregister("String");
        assertThat(EntityIdRegistry.isRegistered("String")).isTrue();
    }

    @Test
    void registryIsCaseSensitive() {
        assertThat(EntityIdRegistry.isRegistered("String")).isTrue();
        assertThat(EntityIdRegistry.isRegistered("string")).isFalse();
    }

    // === Test Fixtures ===

    private static final class TestOrderId implements EntityId {

        private final String value;

        TestOrderId(String value) {
            this.value = value;
        }

        @Override
        public EntityType getType() {
            return new StringEntityType("TestOrder");
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return "OrderId:" + value;
        }
    }

    private static final class TestCustomerId implements EntityId {

        private final String value;

        TestCustomerId(String value) {
            this.value = value;
        }

        @Override
        public EntityType getType() {
            return new StringEntityType("TestCustomer");
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String asTypedString() {
            return "CustomerId:" + value;
        }
    }
}
