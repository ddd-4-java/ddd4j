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

    @Test
    void values_shouldContainTokenAndArgs() {
        assertThat(ApiIdempotentType.values())
                .containsExactlyInAnyOrder(ApiIdempotentType.TOKEN, ApiIdempotentType.ARGS);
    }

    @Test
    void valueOfIgnoreCase_shouldResolveExactName() {
        assertThat(ApiIdempotentType.valueOfIgnoreCase("TOKEN")).isEqualTo(ApiIdempotentType.TOKEN);
        assertThat(ApiIdempotentType.valueOfIgnoreCase("ARGS")).isEqualTo(ApiIdempotentType.ARGS);
    }

    @Test
    void valueOfIgnoreCase_shouldThrowForUnknown() {
        assertThatThrownBy(() -> ApiIdempotentType.valueOfIgnoreCase("UNKNOWN"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void valueOfIgnoreCase_shouldThrowForNull() {
        assertThatThrownBy(() -> ApiIdempotentType.valueOfIgnoreCase(null))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void equals_enum_shouldCompareByName() {
        assertThat(ApiIdempotentType.TOKEN.equals(ApiIdempotentType.TOKEN)).isTrue();
        assertThat(ApiIdempotentType.TOKEN.equals(ApiIdempotentType.ARGS)).isFalse();
    }

    @Test
    void equals_string_shouldCompareIgnoreCaseViaValueOf() {
        assertThat(ApiIdempotentType.TOKEN.equals("TOKEN")).isTrue();
        assertThat(ApiIdempotentType.ARGS.equals("ARGS")).isTrue();
        assertThat(ApiIdempotentType.TOKEN.equals("ARGS")).isFalse();
    }
}
