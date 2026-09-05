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
package io.ddd4j.auth.satoken.subject;

import io.ddd4j.core.subject.Subject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SaTokenSubjectProvider}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class SaTokenSubjectProviderTest {

    @Test
    void getSubject_shouldReturnNonNullSubject() {
        SaTokenSubjectProvider provider = new SaTokenSubjectProvider();

        Subject subject = provider.getSubject();

        assertThat(subject).isNotNull();
    }

    @Test
    void getSubject_shouldReturnSaTokenSubjectInstance() {
        SaTokenSubjectProvider provider = new SaTokenSubjectProvider();

        Subject subject = provider.getSubject();

        assertThat(subject).isInstanceOf(SaTokenSubject.class);
    }

    @Test
    void getSubject_shouldReturnNewInstanceEachCall() {
        SaTokenSubjectProvider provider = new SaTokenSubjectProvider();

        Subject first = provider.getSubject();
        Subject second = provider.getSubject();

        assertThat(first).isNotSameAs(second);
    }

    @Test
    void getSubjectByRealm_shouldReturnNonNullSubject() {
        SaTokenSubjectProvider provider = new SaTokenSubjectProvider();

        Subject subject = provider.getSubject("admin");

        assertThat(subject).isNotNull();
        assertThat(subject).isInstanceOf(SaTokenSubject.class);
    }

    @Test
    void getSubjectByRealm_withNullRealm_shouldStillReturnSubject() {
        SaTokenSubjectProvider provider = new SaTokenSubjectProvider();

        Subject subject = provider.getSubject(null);

        assertThat(subject).isNotNull();
    }
}
