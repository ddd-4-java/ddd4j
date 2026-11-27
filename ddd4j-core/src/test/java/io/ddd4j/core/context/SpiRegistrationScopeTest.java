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

/** \u9a8c\u8bc1 <code>SpiRegistrationScope</code> \u5728<code>shouldRestoreUntypedPredecessor</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class SpiRegistrationScopeTest {

    /** \u6784\u9020\u672c\u7ec4\u6d4b\u8bd5\u8f93\u5165\u65f6\u590d\u7528\u7684\u56fa\u5b9a\u952e\u3002 */
    private static final String KEY = "test.spi.lifecycle";

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>ThreadContext.clear()</code>\u3001<code>BaseContext.clear()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearContexts() {
        ThreadContext.clear();
        BaseContext.clear();
    }

    /** \u9a8c\u8bc1<code>BaseContext.get(KEY, Service.class)</code> \u5305\u542b <code>installed</code>\uff0c<code>BaseContext.get(KEY, Service.class)</code> \u5305\u542b <code>previous</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>BaseContext.get(KEY, Service.class)</code> \u5305\u542b <code>replacement</code>\u3002 */
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


    /** \u9a8c\u8bc1<code>restored</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>previous</code>\u3002 */
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

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalArgumentException.class</code>\uff0c<code>restored</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>previous</code>\uff0c<code>BaseContext.contains(invalidKey)</code>\u4e3a\u5047\u3002 */
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

    /** \u5c06 <code>name</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    private static final class Service {
        /** \u5f53\u524d\u6d4b\u8bd5\u6a21\u578b\u7528\u4e8e\u5c55\u793a\u548c\u65ad\u8a00\u7684\u540d\u79f0\u3002 */
        private final String name;

        /**
         * \u8bbe\u7f6e <code>name</code>\uff0c\u7528\u4e8e\u6784\u9020 <code>Service</code> \u6d4b\u8bd5\u5b9e\u4f8b\u3002
         * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
         */
        public Service(String name) {
            this.name = name;
        }

        /**
         * \u8bfb\u53d6<code>name</code> \u5b57\u6bb5\u3002
         * @return <code>name</code> \u5b57\u6bb5
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
