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
package io.ddd4j.core.util;

import io.ddd4j.core.subject.SubjectProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过 <code>SubjectKit.register()</code>、<code>scope.start()</code>、<code>scope.close()</code> 验证 <code>SubjectKitRegistrationScope</code> 的运行行为。
 */
class SubjectKitRegistrationScopeTest {

    /**
     * 用例修改之前保存的原服务提供器。
     */
    private final SubjectProvider originalProvider = SubjectKit.subjectProvider;

    /**
     * 测试结束后执行 <code>SubjectKit.register()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void restoreSubjectProvider() {
        SubjectKit.register(originalProvider);
    }

    /**
     * 验证<code>SubjectKit.subjectProvider</code> 指向同一实例 <code>current</code>，<code>SubjectKit.subjectProvider</code> 指向同一实例 <code>previous</code>。
     */
    @Test
    void shouldRestorePreviousProviderOnClose() {
        SubjectProvider previous = new SubjectProvider() {
        };
        SubjectProvider current = new SubjectProvider() {
        };
        SubjectKit.register(previous);

        try (SubjectKitRegistrationScope scope = new SubjectKitRegistrationScope(current)) {
            scope.start();
            assertThat(SubjectKit.subjectProvider).isSameAs(current);
        }

        assertThat(SubjectKit.subjectProvider).isSameAs(previous);
    }

    /**
     * 验证<code>SubjectKit.subjectProvider</code> 指向同一实例 <code>replacement</code>。
     */
    @Test
    void shouldNotOverwriteProviderReplacedAfterStart() {
        SubjectProvider current = new SubjectProvider() {
        };
        SubjectProvider replacement = new SubjectProvider() {
        };
        SubjectKitRegistrationScope scope = new SubjectKitRegistrationScope(current);
        scope.start();
        SubjectKit.register(replacement);

        scope.close();

        assertThat(SubjectKit.subjectProvider).isSameAs(replacement);
    }


    /**
     * 验证<code>replacement</code> 等于 <code>current</code>，<code>SubjectKit.subjectProvider</code> 指向同一实例 <code>replacement</code>。
     */
    @Test
    void shouldNotOverwriteEqualButDistinctProviderInstalledAfterStart() {
        SubjectProvider current = new EqualProvider();
        SubjectProvider replacement = new EqualProvider();
        SubjectKit.register(originalProvider);

        try (SubjectKitRegistrationScope scope = new SubjectKitRegistrationScope(current)) {
            scope.start();
            SubjectKit.register(replacement);
            assertThat(replacement).isEqualTo(current).isNotSameAs(current);
        }

        assertThat(SubjectKit.subjectProvider).isSameAs(replacement);
    }

    /**
     * 提供受用例控制的认证主体，便于验证授权路径。
     */
    private static final class EqualProvider implements SubjectProvider {
        /**
         * 比较两个对象的值是否相等。
         *
         * @param obj 用来比较或转换的输入对象
         * @return 对象满足此实现的相等条件时为真
         */
        @Override
        public boolean equals(Object obj) {
            return obj instanceof EqualProvider;
        }

        /**
         * 计算与对象相等条件一致的哈希值。
         *
         * @return 由参与相等比较的状态计算出的哈希值
         */
        @Override
        public int hashCode() {
            return EqualProvider.class.hashCode();
        }
    }
}
