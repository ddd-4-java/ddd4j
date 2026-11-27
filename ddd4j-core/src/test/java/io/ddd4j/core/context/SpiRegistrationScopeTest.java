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
package io.ddd4j.core.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 <code>SpiRegistrationScope</code> 在<code>shouldRestoreUntypedPredecessor</code>等操作中的行为契约。
 */
class SpiRegistrationScopeTest {

    /**
     * 构造本组测试输入时复用的固定键。
     */
    private static final String KEY = "test.spi.lifecycle";

    /**
     * 测试结束后执行 <code>ThreadContext.clear()</code>、<code>BaseContext.clear()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void clearContexts() {
        ThreadContext.clear();
        BaseContext.clear();
    }

    /**
     * 验证<code>BaseContext.get(KEY, Service.class)</code> 包含 <code>installed</code>，<code>BaseContext.get(KEY, Service.class)</code> 包含 <code>previous</code>。
     */
    @Test
    void shouldRestorePreviousServiceOnClose() {
        Service previous = new Service("previous");
        Service installed = new Service("installed");
        BaseContext.inject(KEY, Service.class, previous);

        try (SpiRegistrationScope scope = new SpiRegistrationScope()
                .register(KEY, Service.class, installed)) {
            scope.start();
            assertThat(BaseContext.get(KEY, Service.class)).contains(installed);
        }

        assertThat(BaseContext.get(KEY, Service.class)).contains(previous);
    }

    /**
     * 验证<code>BaseContext.get(KEY, Service.class)</code> 包含 <code>replacement</code>。
     */
    @Test
    void shouldNotRemoveServiceReplacedByApplicationAfterStart() {
        Service installed = new Service("installed");
        Service replacement = new Service("replacement");
        SpiRegistrationScope scope = new SpiRegistrationScope().register(KEY, Service.class, installed);
        scope.start();
        BaseContext.inject(KEY, Service.class, replacement);

        scope.close();

        assertThat(BaseContext.get(KEY, Service.class)).contains(replacement);
    }


    /**
     * 验证<code>restored</code> 指向同一实例 <code>previous</code>。
     */
    @Test
    void shouldRestoreUntypedPredecessorAcrossNestedScopes() {
        Object previous = new Object();
        Service outerService = new Service("outer");
        Service innerService = new Service("inner");
        BaseContext.inject(KEY, previous);

        try (SpiRegistrationScope outer = new SpiRegistrationScope().register(KEY, Service.class, outerService)) {
            outer.start();
            try (SpiRegistrationScope inner = new SpiRegistrationScope().register(KEY, Service.class, innerService)) {
                inner.start();
                assertThat(BaseContext.get(KEY, Service.class)).containsSame(innerService);
            }
            assertThat(BaseContext.get(KEY, Service.class)).containsSame(outerService);
        }

        Object restored = BaseContext.get(KEY);
        assertThat(restored).isSameAs(previous);
    }

    /**
     * 验证被测调用 抛出 <code>IllegalArgumentException.class</code>，<code>restored</code> 指向同一实例 <code>previous</code>，<code>BaseContext.contains(invalidKey)</code>为假。
     */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldRestoreUntypedPredecessorWhenLaterRegistrationFails() {
        Object previous = new Object();
        String invalidKey = KEY + ".invalid";
        BaseContext.inject(KEY, previous);

        try (SpiRegistrationScope scope = new SpiRegistrationScope()
                .register(KEY, Service.class, new Service("installed"))
                .register(invalidKey, (Class) Service.class, "wrong type")) {
            assertThatThrownBy(scope::start).isInstanceOf(IllegalArgumentException.class);
            Object restored = BaseContext.get(KEY);
            assertThat(restored).isSameAs(previous);
            assertThat(BaseContext.contains(invalidKey)).isFalse();
        }
    }

    /**
     * 将 <code>name</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    private static final class Service {
        /**
         * 当前测试模型用于展示和断言的名称。
         */
        private final String name;

        /**
         * 设置 <code>name</code>，用于构造 <code>Service</code> 测试实例。
         *
         * @param name 当前测试指定的对象或属性名称
         */
        public Service(String name) {
            this.name = name;
        }

        /**
         * 读取<code>name</code> 字段。
         *
         * @return <code>name</code> 字段
         */
        public String name() {
            return name;
        }

        /**
         * 根据<code>name</code>比较对象的值是否相等。
         *
         * @param o 用来比较的另一个对象，允许为空
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Service)) {
                return false;
            }
            Service other = (Service) o;
            return Objects.equals(this.name, other.name);
        }

        /**
         * 用<code>name</code>计算哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        /**
         * 生成用于调试和日志的对象描述，包含<code>name</code>。
         *
         * @return 表示当前对象状态的字符串
         */
        @Override
        public String toString() {
            return "Service{" + "name=" + name + "}";
        }

    }
}
