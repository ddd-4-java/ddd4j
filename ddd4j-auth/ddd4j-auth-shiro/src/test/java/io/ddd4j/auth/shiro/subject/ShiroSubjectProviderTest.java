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
package io.ddd4j.auth.shiro.subject;

import io.ddd4j.core.subject.Subject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ShiroSubjectProvider}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ShiroSubjectProviderTest {

    /** \u9a8c\u8bc1<code>subject</code>\u975e\u7a7a\u3002 */
    @Test
    void getSubject_shouldReturnNonNullSubject() {
        ShiroSubjectProvider provider = new ShiroSubjectProvider();

        Subject subject = provider.getSubject();

        assertThat(subject).isNotNull();
    }

    /** \u9a8c\u8bc1<code>subject</code> \u7684\u7c7b\u578b\u4e3a <code>ShiroSubject.class</code>\u3002 */
    @Test
    void getSubject_shouldReturnShiroSubjectInstance() {
        ShiroSubjectProvider provider = new ShiroSubjectProvider();

        Subject subject = provider.getSubject();

        assertThat(subject).isInstanceOf(ShiroSubject.class);
    }

    /** \u9a8c\u8bc1<code>first</code> \u6307\u5411\u4e0d\u540c\u5b9e\u4f8b <code>second</code>\u3002 */
    @Test
    void getSubject_shouldReturnNewInstanceEachCall() {
        ShiroSubjectProvider provider = new ShiroSubjectProvider();

        Subject first = provider.getSubject();
        Subject second = provider.getSubject();

        assertThat(first).isNotSameAs(second);
    }

    /** \u9a8c\u8bc1<code>subject</code>\u975e\u7a7a\uff0c<code>subject</code> \u7684\u7c7b\u578b\u4e3a <code>ShiroSubject.class</code>\u3002 */
    @Test
    void getSubjectByRealm_shouldReturnNonNullSubject() {
        ShiroSubjectProvider provider = new ShiroSubjectProvider();

        Subject subject = provider.getSubject("admin");

        assertThat(subject).isNotNull();
        assertThat(subject).isInstanceOf(ShiroSubject.class);
    }

    /** \u9a8c\u8bc1<code>subject</code>\u975e\u7a7a\u3002 */
    @Test
    void getSubjectByRealm_withNullRealm_shouldStillReturnSubject() {
        ShiroSubjectProvider provider = new ShiroSubjectProvider();

        Subject subject = provider.getSubject(null);

        assertThat(subject).isNotNull();
    }
}
