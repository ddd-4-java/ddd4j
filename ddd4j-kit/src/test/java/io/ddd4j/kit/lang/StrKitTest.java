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
package io.ddd4j.kit.lang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StrKit} (delegates to Hutool {@code StrUtil}).
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class StrKitTest {

    @Test
    void isBlank_shouldReturnTrueForNull() {
        assertThat(StrKit.isBlank(null)).isTrue();
    }

    @Test
    void isBlank_shouldReturnTrueForEmptyString() {
        assertThat(StrKit.isBlank("")).isTrue();
    }

    @Test
    void isBlank_shouldReturnTrueForWhitespaceOnly() {
        assertThat(StrKit.isBlank("   ")).isTrue();
        assertThat(StrKit.isBlank("\t\n")).isTrue();
    }

    @Test
    void isBlank_shouldReturnFalseForNonBlank() {
        assertThat(StrKit.isBlank("a")).isFalse();
        assertThat(StrKit.isBlank(" hello ")).isFalse();
    }

    @Test
    void isEmpty_shouldReturnTrueForNull() {
        assertThat(StrKit.isEmpty(null)).isTrue();
    }

    @Test
    void isEmpty_shouldReturnTrueForEmptyString() {
        assertThat(StrKit.isEmpty("")).isTrue();
    }

    @Test
    void isEmpty_shouldReturnFalseForWhitespace() {
        // isEmpty does NOT treat whitespace as empty (unlike isBlank)
        assertThat(StrKit.isEmpty("  ")).isFalse();
    }

    @Test
    void isEmpty_shouldReturnFalseForNonEmpty() {
        assertThat(StrKit.isEmpty("a")).isFalse();
    }

    @Test
    void isNotBlank_shouldBeNegationOfIsBlank() {
        assertThat(StrKit.isNotBlank("a")).isTrue();
        assertThat(StrKit.isNotBlank(null)).isFalse();
        assertThat(StrKit.isNotBlank("  ")).isFalse();
    }

    @Test
    void isNotEmpty_shouldBeNegationOfIsEmpty() {
        assertThat(StrKit.isNotEmpty("a")).isTrue();
        assertThat(StrKit.isNotEmpty(null)).isFalse();
        assertThat(StrKit.isNotEmpty("  ")).isTrue();
    }

    @Test
    void replaceAll_shouldReplaceEachLiteralOccurrenceWithoutDamagingUnicode() {
        assertThat(StrKit.replaceAll("中", "X", "甲中乙中")).isEqualTo("甲X乙X");
        assertThat(StrKit.replaceAll("aa", "X", "aaaa")).isEqualTo("XX");
        assertThat(StrKit.replaceAll(".", "X", "a.b")).isEqualTo("aXb");
    }

    @Test
    void splitByCharacter_shouldPreserveUnicodeAndFinalSegment() {
        assertThat(StrKit.split("甲|乙|丙", '|')).containsExactly("甲", "乙", "丙");
        assertThat(StrKit.split("甲◇乙", '◇')).containsExactly("甲", "乙");
        assertThat(StrKit.split("|甲||", '|')).containsExactly("", "甲", "", "");
        assertThat(StrKit.split("原文", '|')).containsExactly("原文");
        assertThat(StrKit.split(null, '|')).isEmpty();
    }

}
