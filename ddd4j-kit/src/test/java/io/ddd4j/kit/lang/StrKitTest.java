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

    /**
     * 验证<code>StrKit.isBlank(null)</code>为真。
     */
    @Test
    void isBlank_shouldReturnTrueForNull() {
        assertThat(StrKit.isBlank(null)).isTrue();
    }

    /**
     * 验证<code>StrKit.isBlank("")</code>为真。
     */
    @Test
    void isBlank_shouldReturnTrueForEmptyString() {
        assertThat(StrKit.isBlank("")).isTrue();
    }

    /**
     * 验证<code>StrKit.isBlank(" ")</code>为真，<code>StrKit.isBlank("\t\n")</code>为真。
     */
    @Test
    void isBlank_shouldReturnTrueForWhitespaceOnly() {
        assertThat(StrKit.isBlank("   ")).isTrue();
        assertThat(StrKit.isBlank("\t\n")).isTrue();
    }

    /**
     * 验证<code>StrKit.isBlank("a")</code>为假，<code>StrKit.isBlank(" hello ")</code>为假。
     */
    @Test
    void isBlank_shouldReturnFalseForNonBlank() {
        assertThat(StrKit.isBlank("a")).isFalse();
        assertThat(StrKit.isBlank(" hello ")).isFalse();
    }

    /**
     * 验证<code>StrKit.isEmpty(null)</code>为真。
     */
    @Test
    void isEmpty_shouldReturnTrueForNull() {
        assertThat(StrKit.isEmpty(null)).isTrue();
    }

    /**
     * 验证<code>StrKit.isEmpty("")</code>为真。
     */
    @Test
    void isEmpty_shouldReturnTrueForEmptyString() {
        assertThat(StrKit.isEmpty("")).isTrue();
    }

    /**
     * 验证<code>StrKit.isEmpty(" ")</code>为假。
     */
    @Test
    void isEmpty_shouldReturnFalseForWhitespace() {
        // isEmpty does NOT treat whitespace as empty (unlike isBlank)
        assertThat(StrKit.isEmpty("  ")).isFalse();
    }

    /**
     * 验证<code>StrKit.isEmpty("a")</code>为假。
     */
    @Test
    void isEmpty_shouldReturnFalseForNonEmpty() {
        assertThat(StrKit.isEmpty("a")).isFalse();
    }

    /**
     * 验证<code>StrKit.isNotBlank("a")</code>为真，<code>StrKit.isNotBlank(null)</code>为假，<code>StrKit.isNotBlank(" ")</code>为假。
     */
    @Test
    void isNotBlank_shouldBeNegationOfIsBlank() {
        assertThat(StrKit.isNotBlank("a")).isTrue();
        assertThat(StrKit.isNotBlank(null)).isFalse();
        assertThat(StrKit.isNotBlank("  ")).isFalse();
    }

    /**
     * 验证<code>StrKit.isNotEmpty("a")</code>为真，<code>StrKit.isNotEmpty(null)</code>为假，<code>StrKit.isNotEmpty(" ")</code>为真。
     */
    @Test
    void isNotEmpty_shouldBeNegationOfIsEmpty() {
        assertThat(StrKit.isNotEmpty("a")).isTrue();
        assertThat(StrKit.isNotEmpty(null)).isFalse();
        assertThat(StrKit.isNotEmpty("  ")).isTrue();
    }

    /**
     * 验证按字面替换全部匹配，包含中文和连续重复文本。
     */
    @Test
    void replaceAll_shouldReplaceEachLiteralOccurrenceWithoutDamagingUnicode() {
        assertThat(StrKit.replaceAll("中", "X", "甲中乙中")).isEqualTo("甲X乙X");
        assertThat(StrKit.replaceAll("aa", "X", "aaaa")).isEqualTo("XX");
        assertThat(StrKit.replaceAll(".", "X", "a.b")).isEqualTo("aXb");
    }

    /**
     * 验证<code>StrKit.split("甲|乙|丙", '|')</code> 按顺序仅包含 <code>"甲"</code>、<code>"乙"</code>、<code>"丙"</code>，<code>StrKit.split("甲◇乙", '◇')</code> 按顺序仅包含 <code>"甲"</code>、<code>"乙"</code>，<code>StrKit.split(null, '|')</code>为空。
     */
    @Test
    void splitByCharacter_shouldPreserveUnicodeAndFinalSegment() {
        assertThat(StrKit.split("甲|乙|丙", '|')).containsExactly("甲", "乙", "丙");
        assertThat(StrKit.split("甲◇乙", '◇')).containsExactly("甲", "乙");
        assertThat(StrKit.split("|甲||", '|')).containsExactly("", "甲", "", "");
        assertThat(StrKit.split("原文", '|')).containsExactly("原文");
        assertThat(StrKit.split(null, '|')).isEmpty();
    }

    /**
     * 验证<code>StrKit.trimWhitespace(" ")</code>为空，<code>StrKit.trimWhitespace(" a b ")</code> 等于 <code>"a b"</code>，<code>StrKit.trimTrailingCharacter("///", '/')</code>为空。
     */
    @Test
    void trimMethods_shouldHandleEmptyAndRepeatedBoundaryCharacters() {
        assertThat(StrKit.trimWhitespace("   ")).isEmpty();
        assertThat(StrKit.trimWhitespace("  a b  ")).isEqualTo("a b");
        assertThat(StrKit.trimAllWhitespace(" a b ")).isEqualTo("ab");
        assertThat(StrKit.trimLeadingWhitespace("  a  ")).isEqualTo("a  ");
        assertThat(StrKit.trimTrailingWhitespace("  a  ")).isEqualTo("  a");
        assertThat(StrKit.trimLeadingCharacter("///a/", '/')).isEqualTo("a/");
        assertThat(StrKit.trimTrailingCharacter("/a///", '/')).isEqualTo("/a");
        assertThat(StrKit.trimLeadingCharacter("///", '/')).isEmpty();
        assertThat(StrKit.trimTrailingCharacter("///", '/')).isEmpty();
    }

}
