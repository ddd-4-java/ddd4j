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
package io.ddd4j.annotation.api;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ApiIdempotentType} enum.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ApiIdempotentTypeTest {

    /** \u9a8c\u8bc1<code>ApiIdempotentType.values()</code> \u4ec5\u5305\u542b <code>ApiIdempotentType.TOKEN</code>\u3001<code>ApiIdempotentType.ARGS</code>\u3002 */
    @Test
    void values_shouldContainTokenAndArgs() {
        assertThat(ApiIdempotentType.values())
                .containsExactlyInAnyOrder(ApiIdempotentType.TOKEN, ApiIdempotentType.ARGS);
    }

    /** \u9a8c\u8bc1<code>ApiIdempotentType.valueOfIgnoreCase("TOKEN")</code> \u7b49\u4e8e <code>ApiIdempotentType.TOKEN</code>\uff0c<code>ApiIdempotentType.valueOfIgnoreCase("ARGS")</code> \u7b49\u4e8e <code>ApiIdempotentType.ARGS</code>\u3002 */
    @Test
    void valueOfIgnoreCase_shouldResolveExactName() {
        assertThat(ApiIdempotentType.valueOfIgnoreCase("TOKEN")).isEqualTo(ApiIdempotentType.TOKEN);
        assertThat(ApiIdempotentType.valueOfIgnoreCase("ARGS")).isEqualTo(ApiIdempotentType.ARGS);
    }

    /** \u9a8c\u8bc1<code>ApiIdempotentType.valueOfIgnoreCase("UNKNOWN")</code> \u629b\u51fa <code>NoSuchElementException.class</code>\u3002 */
    @Test
    void valueOfIgnoreCase_shouldThrowForUnknown() {
        assertThatThrownBy(() -> ApiIdempotentType.valueOfIgnoreCase("UNKNOWN"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("UNKNOWN");
    }

    /** \u9a8c\u8bc1<code>ApiIdempotentType.valueOfIgnoreCase(null)</code> \u629b\u51fa <code>NoSuchElementException.class</code>\u3002 */
    @Test
    void valueOfIgnoreCase_shouldThrowForNull() {
        assertThatThrownBy(() -> ApiIdempotentType.valueOfIgnoreCase(null))
                .isInstanceOf(NoSuchElementException.class);
    }

    /** \u9a8c\u8bc1<code>ApiIdempotentType.TOKEN.equals(ApiIdempotentType.TOKEN)</code>\u4e3a\u771f\uff0c<code>ApiIdempotentType.TOKEN.equals(ApiIdempotentType.ARGS)</code>\u4e3a\u5047\u3002 */
    @Test
    void equals_enum_shouldCompareByName() {
        assertThat(ApiIdempotentType.TOKEN.equals(ApiIdempotentType.TOKEN)).isTrue();
        assertThat(ApiIdempotentType.TOKEN.equals(ApiIdempotentType.ARGS)).isFalse();
    }

    /** \u9a8c\u8bc1<code>ApiIdempotentType.TOKEN.equals("TOKEN")</code>\u4e3a\u771f\uff0c<code>ApiIdempotentType.ARGS.equals("ARGS")</code>\u4e3a\u771f\uff0c<code>ApiIdempotentType.TOKEN.equals("ARGS")</code>\u4e3a\u5047\u3002 */
    @Test
    void equals_string_shouldCompareIgnoreCaseViaValueOf() {
        assertThat(ApiIdempotentType.TOKEN.equals("TOKEN")).isTrue();
        assertThat(ApiIdempotentType.ARGS.equals("ARGS")).isTrue();
        assertThat(ApiIdempotentType.TOKEN.equals("ARGS")).isFalse();
    }
}
