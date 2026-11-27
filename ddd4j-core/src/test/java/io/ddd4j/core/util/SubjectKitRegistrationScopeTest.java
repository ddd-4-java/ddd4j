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
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class SubjectKitRegistrationScopeTest {

    /** \u7528\u4f8b\u4fee\u6539\u4e4b\u524d\u4fdd\u5b58\u7684\u539f\u670d\u52a1\u63d0\u4f9b\u5668\u3002 */
    private final SubjectProvider originalProvider = SubjectKit.subjectProvider;

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>SubjectKit.register()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void restoreSubjectProvider() {
        SubjectKit.register(originalProvider);
    }

    /** \u9a8c\u8bc1<code>SubjectKit.subjectProvider</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>current</code>\uff0c<code>SubjectKit.subjectProvider</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>previous</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>SubjectKit.subjectProvider</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>replacement</code>\u3002 */
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
}
