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
package io.ddd4j.auth.security.subject;

import io.ddd4j.core.subject.Subject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SecuritySubjectProvider}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class SecuritySubjectProviderTest {

    /** \u9a8c\u8bc1<code>subject</code>\u975e\u7a7a\u3002 */
    @Test
    void getSubject_shouldReturnNonNullSubject() {
        SecuritySubjectProvider provider = new SecuritySubjectProvider();

        Subject subject = provider.getSubject();

        assertThat(subject).isNotNull();
    }

    /** \u9a8c\u8bc1<code>subject</code> \u7684\u7c7b\u578b\u4e3a <code>SecuritySubject.class</code>\u3002 */
    @Test
    void getSubject_shouldReturnSecuritySubjectInstance() {
        SecuritySubjectProvider provider = new SecuritySubjectProvider();

        Subject subject = provider.getSubject();

        assertThat(subject).isInstanceOf(SecuritySubject.class);
    }

    /** \u9a8c\u8bc1<code>first</code> \u6307\u5411\u4e0d\u540c\u5b9e\u4f8b <code>second</code>\u3002 */
    @Test
    void getSubject_shouldReturnNewInstanceEachCall() {
        SecuritySubjectProvider provider = new SecuritySubjectProvider();

        Subject first = provider.getSubject();
        Subject second = provider.getSubject();

        assertThat(first).isNotSameAs(second);
    }
}
