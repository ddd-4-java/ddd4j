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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EntityIdPath} 校验三件套与路径导航代数的契约测试。
 *
 * <p>覆盖 README 索引第 02 项「EntityIdPath 补 validate」的 ddd4j 落地：
 * {@code isValid}/{@code valueOf} 分离——布尔校验对任意输入返回 {@code false}
 * 而不抛异常（修掉 fuin 先例的静默 {@code null}），构造转换则对坏输入抛出
 * 携带出错段原文的 {@link IllegalArgumentException}；两类判定必须一致。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
class EntityIdPathTest {

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

    @Test
    void isValidNeverThrowsOnGarbageInput() {
        String[] garbage = {"/", "\\", "::", "::::", "/:", "String:order-1//", "a/b/c", "\\:", "x\\/:y/"};
        for (String input : garbage) {
            assertThat(EntityIdPath.isValid(input)).isFalse();
        }
    }

    @Test
    void isValidAgreesWithValueOf() {
        String valid = "String:order-1/String:item-9";
        assertThat(EntityIdPath.isValid(valid)).isTrue();
        // 序列化对偶：解析后 asString() 还原原文
        assertThat(EntityIdPath.valueOf(valid).asString()).isEqualTo(valid);
    }

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

    @Test
    void singleElementPathReturnsNullForRestAndParent() {
        EntityIdPath single = EntityIdPath.valueOf("String:order-1");

        assertThat(single.size()).isEqualTo(1);
        assertThat(single.rest()).isNull();
        assertThat(single.parent()).isNull();
    }
}
