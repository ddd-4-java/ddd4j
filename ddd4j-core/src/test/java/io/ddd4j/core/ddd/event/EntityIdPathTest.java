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

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link EntityIdPath} 反序列化与转义测试。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
class EntityIdPathTest {

    /**
     * 验证<code>descriptor.getName()</code> 不等于 <code>"java.util.CollSer"</code>，<code>input.readObject()</code> 等于 <code>path</code>。
     *
     * @throws Exception 准备或读取夹具文件时失败
     */
    @Test
    void serializedPathUsesListTypesReadableOnJava8() throws Exception {
        EntityIdPath path = new EntityIdPath(new StringEntityId("order/1"), new StringEntityId("line:1"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(path);
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass descriptor) throws IOException, ClassNotFoundException {
                assertThat(descriptor.getName()).isNotEqualTo("java.util.CollSer");
                return super.resolveClass(descriptor);
            }
        }) {
            assertThat(input.readObject()).isEqualTo(path);
        }
    }

    /**
     * 验证<code>path.first().asString()</code> 等于 <code>"order-1"</code>，<code>path.size()</code> 等于 <code>2</code>，<code>path.asString()</code> 等于 <code>"String:order-1/String:line-1"</code>。
     */
    @Test
    void sourceListAndReturnedIteratorCannotMutateThePath() {
        List<EntityId> source = new ArrayList<>(Arrays.asList(
                new StringEntityId("order-1"), new StringEntityId("line-1")));
        EntityIdPath path = new EntityIdPath(source);

        source.set(0, new StringEntityId("changed"));
        assertThat(path.first().asString()).isEqualTo("order-1");

        Iterator<EntityId> iterator = path.iterator();
        iterator.next();
        iterator.remove();
        assertThat(path.size()).isEqualTo(2);
        assertThat(path.asString()).isEqualTo("String:order-1/String:line-1");
    }

    /**
     * 测试结束后执行 <code>EntityIdRegistry.unregister()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void cleanup() {
        // 清理注册表，避免污染其他测试
        EntityIdRegistry.unregister("OrderId");
        EntityIdRegistry.unregister("CustomerId");
    }

    /**
     * 验证<code>single.size()</code> 等于 <code>1</code>，<code>single.last().asString()</code> 等于 <code>"order-1"</code>，<code>multi.last().asString()</code> 等于 <code>"item-9"</code>。
     */
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

    /**
     * 验证<code>EntityIdPath.valueOf("")</code> 抛出 <code>IllegalArgumentException</code>，<code>EntityIdPath.valueOf("String:")</code> 抛出 <code>IllegalArgumentException</code>。
     */
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

    /**
     * 验证<code>EntityIdPath.valueOf(original.asString()).asString()</code> 等于 <code>original.asString()</code>。
     */
    @Test
    void valueOfIsIdempotentForSerializedForm() {
        EntityIdPath original = new EntityIdPath(new StringEntityId("order-1"), new StringEntityId("item-9"));

        assertThat(EntityIdPath.valueOf(original.asString()).asString()).isEqualTo(original.asString());
    }

    /**
     * 验证<code>path.size()</code> 等于 <code>1</code>，<code>(Object) path.last()</code> 的类型为 <code>StringEntityId.class</code>，<code>path.last().asString()</code> 等于 <code>"abc-123"</code>。
     */
    @Test
    void unregisteredTypeFallsBackToStringEntityId() {
        EntityIdPath path = EntityIdPath.valueOf("Unknown:abc-123");

        assertThat(path.size()).isEqualTo(1);
        assertThat((Object) path.last()).isInstanceOf(StringEntityId.class);
        assertThat(path.last().asString()).isEqualTo("abc-123");
    }

    /**
     * 验证<code>path.size()</code> 等于 <code>1</code>，<code>(Object) path.last()</code> 的类型为 <code>TestOrderId.class</code>，<code>path.last().getType().asString()</code> 等于 <code>"TestOrder"</code>。
     */
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

    /**
     * 验证<code>restored.size()</code> 等于 <code>2</code>，<code>(Object) restored.first()</code> 的类型为 <code>TestOrderId.class</code>，<code>restored.last().asString()</code> 等于 <code>"c-9"</code>。
     */
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

    /**
     * 验证<code>serialized</code> 不包含 <code>"\\"</code>，<code>restored.last().asString()</code> 等于 <code>"o:1"</code>。
     */
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

    /**
     * 验证<code>serialized</code> 等于 <code>"OrderId:o-1"</code>，<code>serialized</code> 不包含 <code>"\\"</code>。
     */
    @Test
    void simplePathsAreNotEscaped() {
        // 简单路径（值不含段分隔符 / 或反斜杠）保持原样输出，无视觉噪音
        EntityIdPath path = new EntityIdPath(new TestOrderId("o-1"));
        String serialized = path.asString();

        assertThat(serialized).isEqualTo("OrderId:o-1");
        assertThat(serialized).doesNotContain("\\");
    }

    /**
     * 验证<code>serialized</code> 包含 <code>"\\/"</code>，<code>restored.last().asString()</code> 等于 <code>"o/1"</code>。
     */
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

    /**
     * 验证<code>serialized</code> 包含 <code>"\\\\"</code>，<code>restored.last().asString()</code> 等于 <code>"a\\b"</code>。
     */
    @Test
    void valuesContainingBackslashAreEscapedAndRecovered() {
        EntityIdPath path = new EntityIdPath(new TestOrderId("a\\b"));
        String serialized = path.asString();

        // 反斜杠转义为双反斜杠
        assertThat(serialized).contains("\\\\");

        EntityIdPath restored = EntityIdPath.valueOf(serialized);
        assertThat(restored.last().asString()).isEqualTo("a\\b");
    }

    /**
     * 验证<code>restored.size()</code> 等于 <code>3</code>，<code>restored</code> 等于 <code>original</code>。
     */
    @Test
    void separatorAfterEscapedBackslashesKeepsEachEntitySegment() {
        EntityIdPath original = new EntityIdPath(
                new StringEntityId("order\\"),
                new StringEntityId("line\\/2"),
                new StringEntityId("item:3"));

        EntityIdPath restored = EntityIdPath.valueOf(original.asString());

        assertThat(restored.size()).isEqualTo(3);
        assertThat(restored).isEqualTo(original);
    }

    /**
     * 验证<code>EntityIdPath.isValid(validPath)</code>为真，<code>EntityIdPath.isValid(malformedPath)</code>为假。
     */
    @Test
    void separatorAfterEscapedBackslashesStillRejectsTrailingEmptySegment() {
        String validPath = new EntityIdPath(new StringEntityId("order\\")).asString();
        String malformedPath = validPath + EntityIdPath.PATH_SEPARATOR;

        assertThat(EntityIdPath.isValid(validPath)).isTrue();
        assertThat(EntityIdPath.isValid(malformedPath)).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> EntityIdPath.valueOf(malformedPath))
                .withMessageContaining("blank segment");
    }

    /**
     * 验证<code>EntityIdRegistry.isRegistered("String")</code>为真。
     */
    @Test
    void registryRefusesToUnregisterBuiltinStringType() {
        // 试图移除 StringEntityId 不应生效
        EntityIdRegistry.unregister("String");
        assertThat(EntityIdRegistry.isRegistered("String")).isTrue();
    }

    /**
     * 验证<code>EntityIdRegistry.isRegistered("String")</code>为真，<code>EntityIdRegistry.isRegistered("string")</code>为假。
     */
    @Test
    void registryIsCaseSensitive() {
        assertThat(EntityIdRegistry.isRegistered("String")).isTrue();
        assertThat(EntityIdRegistry.isRegistered("string")).isFalse();
    }

    /**
     * 验证<code>EntityIdPath.isValid("String:order-1")</code>为真，<code>EntityIdPath.isValid("String:order-1/String:item-9")</code>为真，<code>EntityIdPath.isValid("String:a\\/b")</code>为真。
     */
    @Test
    void isValidAcceptsWellFormedPaths() {
        // 单段与多段
        assertThat(EntityIdPath.isValid("String:order-1")).isTrue();
        assertThat(EntityIdPath.isValid("String:order-1/String:item-9")).isTrue();
        // 值内冒号无需转义——「首个 : 切分」约定下全部归属 value
        assertThat(EntityIdPath.isValid("String:a:b:c")).isTrue();
        // 值内转义的 \/ 不是段分隔符
        assertThat(EntityIdPath.isValid("String:a\\/b")).isTrue();
    }

    /**
     * 验证<code>EntityIdPath.isValid(null)</code>为假，<code>EntityIdPath.isValid("")</code>为假，<code>EntityIdPath.isValid("String:a//b")</code>为假。
     */
    @Test
    void isValidRejectsMalformedPaths() {
        assertThat(EntityIdPath.isValid(null)).isFalse();
        assertThat(EntityIdPath.isValid("")).isFalse();
        assertThat(EntityIdPath.isValid("   ")).isFalse();
        // 缺 Type:value 形态
        assertThat(EntityIdPath.isValid("order-1")).isFalse();
        // 尾部空段
        assertThat(EntityIdPath.isValid("String:order-1/")).isFalse();
        // 空 type / 空 value
        assertThat(EntityIdPath.isValid(":order-1")).isFalse();
        assertThat(EntityIdPath.isValid("String:")).isFalse();
        // 中间空段
        assertThat(EntityIdPath.isValid("String:a//b")).isFalse();
    }

    /**
     * 验证<code>EntityIdPath.isValid(input)</code>为假。
     */
    @Test
    void isValidNeverThrowsOnGarbageInput() {
        String[] garbage = {"/", "\\", "::", "::::", "/:", "String:order-1//", "a/b/c", "\\:", "x\\/:y/"};
        for (String input : garbage) {
            assertThat(EntityIdPath.isValid(input)).isFalse();
        }
    }

    /**
     * 验证<code>EntityIdPath.isValid(valid)</code>为真，<code>EntityIdPath.valueOf(valid).asString()</code> 等于 <code>valid</code>。
     */
    @Test
    void isValidAgreesWithValueOf() {
        String valid = "String:order-1/String:item-9";
        assertThat(EntityIdPath.isValid(valid)).isTrue();
        // 序列化对偶：解析后 asString() 还原原文
        assertThat(EntityIdPath.valueOf(valid).asString()).isEqualTo(valid);
    }

    /**
     * 验证<code>path.size()</code> 等于 <code>2</code>，<code>path.first().asString()</code> 等于 <code>"order-1"</code>，<code>path.parent().asString()</code> 等于 <code>"String:order-1"</code>。
     */
    @Test
    void navigationAlgebraFollowsEventRoutingContract() {
        EntityIdPath path = EntityIdPath.valueOf("String:order-1/String:item-9");

        assertThat(path.size()).isEqualTo(2);
        // 首段恒为聚合根标识、末段为事件源标识
        assertThat(path.first().asString()).isEqualTo("order-1");
        assertThat(path.last().asString()).isEqualTo("item-9");
        // rest 去首段、parent 去末段
        assertThat(path.rest().asString()).isEqualTo("String:item-9");
        assertThat(path.parent().asString()).isEqualTo("String:order-1");
    }

    /**
     * 验证<code>single.size()</code> 等于 <code>1</code>，<code>single.rest()</code>为空，<code>single.parent()</code>为空。
     */
    @Test
    void singleElementPathReturnsNullForRestAndParent() {
        EntityIdPath single = EntityIdPath.valueOf("String:order-1");

        assertThat(single.size()).isEqualTo(1);
        assertThat(single.rest()).isNull();
        assertThat(single.parent()).isNull();
    }
    // === Test Fixtures ===

    /**
     * 为测试实体提供独立标识的值对象。
     */
    private static final class TestOrderId implements EntityId {

        /**
         * 组成测试标识的原始值。
         */
        private final String value;

        /**
         * 设置 <code>value</code>，用于构造 <code>TestOrderId</code> 测试实例。
         *
         * @param value 组成测试标识的原始值
         */
        TestOrderId(String value) {
            this.value = value;
        }

        /**
         * 返回当前值或标识公开的类型。
         *
         * @return 实体类型
         */
        @Override
        public EntityType getType() {
            return new StringEntityType("TestOrder");
        }

        /**
         * 返回聚合或实体标识的业务字符串。
         *
         * @return 实际返回<code>value</code>，供调用方取得当前值
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * 返回带类型前缀的标识字符串。
         *
         * @return 实际返回<code>"OrderId:" + value</code>，供调用方取得当前值
         */
        @Override
        public String asTypedString() {
            return "OrderId:" + value;
        }
    }

    /**
     * 为测试实体提供独立标识的值对象。
     */
    private static final class TestCustomerId implements EntityId {

        /**
         * 组成测试标识的原始值。
         */
        private final String value;

        /**
         * 设置 <code>value</code>，用于构造 <code>TestCustomerId</code> 测试实例。
         *
         * @param value 组成测试标识的原始值
         */
        TestCustomerId(String value) {
            this.value = value;
        }

        /**
         * 返回当前值或标识公开的类型。
         *
         * @return 实体类型
         */
        @Override
        public EntityType getType() {
            return new StringEntityType("TestCustomer");
        }

        /**
         * 返回聚合或实体标识的业务字符串。
         *
         * @return 实际返回<code>value</code>，供调用方取得当前值
         */
        @Override
        public String asString() {
            return value;
        }

        /**
         * 返回带类型前缀的标识字符串。
         *
         * @return 实际返回<code>"CustomerId:" + value</code>，供调用方取得当前值
         */
        @Override
        public String asTypedString() {
            return "CustomerId:" + value;
        }
    }
}
